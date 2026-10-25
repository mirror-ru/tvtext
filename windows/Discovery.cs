using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Net;
using System.Runtime.InteropServices;
using System.Threading;
using System.Threading.Tasks;

namespace TvText
{
    public sealed class DiscoveredTv
    {
        public string Name, Endpoint, DeviceId;
        internal string LocalName;
        public override string ToString() { return String.IsNullOrEmpty(LocalName)?Name:LocalName; }
    }

    // Uses Windows' DNS Client service; Bonjour and a separate multicast listener
    // are unnecessary. Discovery results are untrusted; TLS pinning authenticates TV.
    public sealed class Discovery : IDisposable
    {
        public const string ServiceType = "_tvtext._tcp.local";
        private readonly object gate = new object();
        private readonly HashSet<string> names = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        private readonly Dictionary<string, DiscoveredTv> found = new Dictionary<string, DiscoveredTv>();
        private readonly List<Operation> operations = new List<Operation>();
        private bool stopped;
        private uint failure;

        internal static int PendingOperations{get{return Roots.Count;}}
        public static async Task<List<DiscoveredTv>> FindAsync(int milliseconds,CancellationToken cancellationToken=default(CancellationToken))
        {
            if(milliseconds<0)throw new ArgumentOutOfRangeException("milliseconds");
            cancellationToken.ThrowIfCancellationRequested();
            using (Discovery search = new Discovery())
            {
                search.Start();await Task.Delay(milliseconds,cancellationToken).ConfigureAwait(false);
                lock (search.gate)
                {
                    if (search.failure != 0) throw new InvalidOperationException(UiText.T("discovery_failed"));
                    List<DiscoveredTv> result=new List<DiscoveredTv>(search.found.Values);
                    result.Sort(delegate(DiscoveredTv a,DiscoveredTv b){int name=StringComparer.CurrentCultureIgnoreCase.Compare(a.Name,b.Name);if(name!=0)return name;int id=StringComparer.Ordinal.Compare(a.DeviceId,b.DeviceId);return id!=0?id:StringComparer.Ordinal.Compare(a.Endpoint,b.Endpoint);});
                    return result;
                }
            }
        }
        private void Start()
        {
            Operation browse = new Operation(this, true, ServiceType); lock (gate) operations.Add(browse);
            uint code = browse.Start(); if (code != 9506) throw new InvalidOperationException(UiText.T("discovery_failed"));
        }
        private void Resolve(string name)
        {
            Operation resolve;
            lock (gate)
            {
                if (stopped || names.Count >= 32 || !names.Add(name)) return;
                resolve = new Operation(this, false, name); operations.Add(resolve);
            }
            resolve.Start();
            bool cancel; lock(gate) cancel = stopped;
            if(cancel) resolve.Cancel();
        }
        private void Add(DiscoveredTv tv)
        {
            if (tv == null) return;
            lock (gate) { if (!stopped) found[tv.DeviceId + "|" + tv.Endpoint] = tv; }
        }
        public void Dispose()
        {
            Operation[] pending;
            lock (gate) { stopped = true; pending = operations.ToArray(); }
            foreach (Operation operation in pending) operation.Cancel();
        }

        [UnmanagedFunctionPointer(CallingConvention.Winapi)]
        private delegate void NativeCallback(uint status, IntPtr context, IntPtr result);
        private static readonly NativeCallback BrowseCallback = OnBrowse, ResolveCallback = OnResolve;
        private static readonly ConcurrentDictionary<IntPtr, Operation> Roots = new ConcurrentDictionary<IntPtr, Operation>();
        private static int nextId;
        [StructLayout(LayoutKind.Sequential)]
        private struct Request { public uint Version, InterfaceIndex; public IntPtr QueryName, Callback, Context; }
        [StructLayout(LayoutKind.Sequential)]
        private struct Record { public IntPtr Next, Name; public ushort Type, DataLength; public uint Flags, Ttl, Reserved; public IntPtr Data; }
        [StructLayout(LayoutKind.Sequential)]
        private struct Instance
        {
            public IntPtr Name, Host, Ip4, Ip6;
            public ushort Port, Priority, Weight;
            public uint PropertyCount;
            public IntPtr Keys, Values;
            public uint InterfaceIndex;
        }
        [DllImport("dnsapi.dll", ExactSpelling = true)] private static extern uint DnsServiceBrowse(ref Request request, IntPtr cancel);
        [DllImport("dnsapi.dll", ExactSpelling = true)] private static extern uint DnsServiceResolve(ref Request request, IntPtr cancel);
        [DllImport("dnsapi.dll", ExactSpelling = true)] private static extern uint DnsServiceBrowseCancel(IntPtr cancel);
        [DllImport("dnsapi.dll", ExactSpelling = true)] private static extern uint DnsServiceResolveCancel(IntPtr cancel);
        [DllImport("dnsapi.dll", ExactSpelling = true)] private static extern void DnsRecordListFree(IntPtr record, int freeType);
        [DllImport("dnsapi.dll", ExactSpelling = true)] private static extern void DnsServiceFreeInstance(IntPtr instance);

        private static void OnBrowse(uint status, IntPtr context, IntPtr records)
        {
            Operation operation;
            try
            {
                if (!Roots.TryGetValue(context, out operation)) return;
                if (status != 0)
                {
                    if (status != 995) lock (operation.Owner.gate) operation.Owner.failure = status;
                    operation.Complete(); return;
                }
                IntPtr current = records;
                for (int i = 0; current != IntPtr.Zero && i < 256; i++)
                {
                    Record record = (Record)Marshal.PtrToStructure(current, typeof(Record));
                    if (record.Type == 12 && record.Ttl > 0 && record.Data != IntPtr.Zero)
                    {
                        string owner = Marshal.PtrToStringUni(record.Name) ?? "";
                        string name = Marshal.PtrToStringUni(record.Data) ?? "";
                        if (String.Equals(owner.TrimEnd('.'), ServiceType, StringComparison.OrdinalIgnoreCase)
                            && name.TrimEnd('.').EndsWith("." + ServiceType, StringComparison.OrdinalIgnoreCase))
                            operation.Owner.Resolve(name);
                    }
                    current = record.Next;
                }
            }
            catch { /* Never propagate an exception across an unmanaged callback. */ }
            finally { if (records != IntPtr.Zero) DnsRecordListFree(records, 1); }
        }
        private static void OnResolve(uint status, IntPtr context, IntPtr result)
        {
            Operation operation = null;
            try
            {
                if (!Roots.TryGetValue(context, out operation) || status != 0 || result == IntPtr.Zero) return;
                operation.Owner.Add(ReadInstance(result));
            }
            catch { }
            finally { if (result != IntPtr.Zero) DnsServiceFreeInstance(result); if (operation != null) operation.Complete(); }
        }
        internal static DiscoveredTv ReadInstance(IntPtr pointer)
        {
            Instance instance = (Instance)Marshal.PtrToStructure(pointer, typeof(Instance));
            if (instance.Ip4 == IntPtr.Zero && instance.Ip6 == IntPtr.Zero || instance.Port == 0 || instance.PropertyCount > 64) return null;
            string id = "", version = "";bool ipv4Only=false;
            for (int i = 0; i < instance.PropertyCount && instance.Keys != IntPtr.Zero && instance.Values != IntPtr.Zero; i++)
            {
                string key = Marshal.PtrToStringUni(Marshal.ReadIntPtr(instance.Keys, i * IntPtr.Size));
                string value = Marshal.PtrToStringUni(Marshal.ReadIntPtr(instance.Values, i * IntPtr.Size)) ?? "";
                if (key == "id") id = value; if (key == "v") version = value;
                if(key=="ipv4-only")ipv4Only=value=="1";
            }
            Guid parsed;
            if (version != "1" || !Guid.TryParse(id, out parsed)) return null;
            if(ipv4Only&&instance.Ip4==IntPtr.Zero)return null;
            IPAddress address;
            if(instance.Ip4!=IntPtr.Zero){byte[] ip=new byte[4];Marshal.Copy(instance.Ip4,ip,0,4);address=new IPAddress(ip);}
            else
            {
                byte[] ip=new byte[16];Marshal.Copy(instance.Ip6,ip,0,16);address=new IPAddress(ip);
                if(address.IsIPv6LinkLocal)address.ScopeId=instance.InterfaceIndex;
            }
            string name = (Marshal.PtrToStringUni(instance.Name) ?? "TVText").TrimEnd('.');
            string suffix = "." + ServiceType;
            if (!name.EndsWith(suffix, StringComparison.OrdinalIgnoreCase)) return null;
            name = name.Substring(0, name.Length - suffix.Length);
            return new DiscoveredTv { Name = name, DeviceId = parsed.ToString(), Endpoint = Client.FormatEndpoint(address,instance.Port) };
        }

        private sealed class Operation
        {
            internal readonly Discovery Owner;
            private readonly bool browse;
            private readonly object lifetime = new object();
            private readonly IntPtr id;
            private IntPtr query, cancel;
            private bool completed;
            private int calls;
            internal Operation(Discovery owner, bool isBrowse, string name)
            {
                Owner = owner; browse = isBrowse; id = new IntPtr(Interlocked.Increment(ref nextId));
                query = Marshal.StringToHGlobalUni(name); cancel = Marshal.AllocHGlobal(IntPtr.Size); Marshal.WriteIntPtr(cancel, IntPtr.Zero);
                Roots[id] = this;
            }
            internal uint Start()
            {
                lock (lifetime) calls++;
                try
                {
                    Request request = new Request { Version = 1, QueryName = query,
                        Callback = Marshal.GetFunctionPointerForDelegate(browse ? BrowseCallback : ResolveCallback), Context = id };
                    uint status = browse ? DnsServiceBrowse(ref request, cancel) : DnsServiceResolve(ref request, cancel);
                    if (status != 9506) Complete();
                    return status;
                }
                catch { Complete(); throw; }
                finally { EndCall(); }
            }
            internal void Cancel()
            {
                lock (lifetime) { if (completed) return; calls++; }
                try { if (browse) DnsServiceBrowseCancel(cancel); else DnsServiceResolveCancel(cancel); }
                finally { EndCall(); }
            }
            internal void Complete() { lock (lifetime) { completed = true; ReleaseIfIdle(); } }
            private void EndCall() { lock (lifetime) { calls--; ReleaseIfIdle(); } }
            private void ReleaseIfIdle()
            {
                // Native cancel handles stay alive through the final callback, including
                // callbacks that arrive synchronously before the API call has returned.
                if (!completed || calls != 0 || cancel == IntPtr.Zero) return;
                Marshal.FreeHGlobal(query); Marshal.FreeHGlobal(cancel); query = cancel = IntPtr.Zero;
                Operation ignored; Roots.TryRemove(id, out ignored);
            }
        }
    }
}
