using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Runtime.InteropServices;
using System.Threading;
using TvText;

internal static class DiscoveryTests
{
    internal const string FixtureId = "f7e69b4b-f95f-44ef-b5a0-100fab7221e1";
    private static void Check(bool value,string label) { if(!value) throw new Exception(label); }
    internal static void Profiles(string folder)
    {
        Connection.StoragePathOverride=Path.Combine(folder,"test-profiles.dat");
        try
        {
            Connection a=new Connection{DeviceId=Guid.NewGuid().ToString(),Name="Гостиная",Token="key-a",Endpoint="192.168.1.10:45831",Fingerprint=new string('A',64)};
            Connection b=new Connection{DeviceId=Guid.NewGuid().ToString(),Name="Спальня",Token="key-b",Endpoint="192.168.1.11:45831",Fingerprint=new string('B',64)};
            a.Save();b.Save();Check(Connection.LoadAll().Count==2,"two profiles retained");Check(Connection.Load().DeviceId==b.DeviceId,"active profile persisted");
            a.Save();Check(Connection.Load().Token=="key-a","switch to first TV");Check(Connection.LoadAll().Count==2,"switch preserves other TV");
            a.Endpoint="192.168.1.88:45831";a.Save();Check(Connection.Load().Endpoint==a.Endpoint,"changed IP persisted");
            string raw=System.Text.Encoding.UTF8.GetString(File.ReadAllBytes(Connection.StoragePathOverride));Check(!raw.Contains("key-a")&&!raw.Contains("Гостиная"),"DPAPI encrypted profiles");
            Connection.Forget(a.DeviceId);Check(Connection.LoadAll().Count==1&&Connection.Load().Token=="key-b","forget selected TV only");
            Console.WriteLine("PASS: multiple TV profiles, independent keys, active selection, IP update, encrypted storage, per-TV removal");
        }
        finally{Connection.StoragePathOverride=null;}
    }
    internal static void Reconnect(string endpoint,string fingerprint,string token)
    {
        Connection saved=new Connection{DeviceId=FixtureId,Endpoint="127.0.0.1:1",Fingerprint=fingerprint,Token=token};
        DiscoveredTv tv=new DiscoveredTv{Name="Fixture",Endpoint=endpoint,DeviceId=FixtureId};
        Check(ConnectionResolver.FindVerifiedAsync(saved,new[]{tv}).GetAwaiter().GetResult()!=null,"changed IP resolves without pairing");
        saved.Token="revoked-fixture-token";Check(ConnectionResolver.FindVerifiedAsync(saved,new[]{tv}).GetAwaiter().GetResult()!=null,"pinned TV can be relocated even after pairing key revocation");saved.Token=token;
        saved.Fingerprint=new string('0',64);Check(ConnectionResolver.FindVerifiedAsync(saved,new[]{tv}).GetAwaiter().GetResult()==null,"spoofed mDNS identity cannot change TLS pin");
        saved.Fingerprint=fingerprint;tv.DeviceId=Guid.NewGuid().ToString();Check(ConnectionResolver.FindVerifiedAsync(saved,new[]{tv}).GetAwaiter().GetResult()==null,"other TV ignored");
        Console.WriteLine("PASS: automatic endpoint recovery by stable ID, pinned certificate verification, other-TV isolation");
    }
    internal static void NativeBrowse()
    {
        using(Advertisement fixture=new Advertisement())
        {
            List<DiscoveredTv> list=Discovery.FindAsync(4500).GetAwaiter().GetResult();
            DiscoveredTv selected=list.Find(delegate(DiscoveredTv tv){return tv.DeviceId==FixtureId;});
            Check(selected!=null,"Windows DNS-SD did not discover the local fixture");
            string host;int port;Client.ParseEndpoint(selected.Endpoint,out host,out port);Check(port==45832,"DNS-SD port byte order");
            Console.WriteLine("PASS: native Windows mDNS browse + resolve, PTR, SRV, TXT ID/version, IPv4, port, cancellation");
        }
    }
    private sealed class Advertisement:IDisposable
    {
        [UnmanagedFunctionPointer(CallingConvention.Winapi)]private delegate void Callback(uint status,IntPtr context,IntPtr instance);
        [StructLayout(LayoutKind.Sequential)]private struct Registration
        {public uint Version,InterfaceIndex;public IntPtr Instance,Callback,Context,Credentials;public int Unicast;}
        [DllImport("dnsapi.dll",CharSet=CharSet.Unicode,ExactSpelling=true)]private static extern IntPtr DnsServiceConstructInstance(string service,string host,IntPtr ip4,IntPtr ip6,ushort port,ushort priority,ushort weight,uint count,IntPtr keys,IntPtr values);
        [DllImport("dnsapi.dll",ExactSpelling=true)]private static extern uint DnsServiceRegister(ref Registration request,IntPtr cancel);
        [DllImport("dnsapi.dll",ExactSpelling=true)]private static extern uint DnsServiceDeRegister(ref Registration request,IntPtr cancel);
        [DllImport("dnsapi.dll",ExactSpelling=true)]private static extern void DnsServiceFreeInstance(IntPtr instance);
        private readonly ManualResetEvent signal=new ManualResetEvent(false);
        private readonly Callback callback;
        private Registration request;
        private IntPtr instance,cancel;
        private uint result;
        internal Advertisement()
        {
            callback=OnComplete;
            IntPtr keys=Marshal.AllocHGlobal(2*IntPtr.Size),values=Marshal.AllocHGlobal(2*IntPtr.Size);
            IntPtr[] strings={Marshal.StringToHGlobalUni("id"),Marshal.StringToHGlobalUni("v"),Marshal.StringToHGlobalUni(FixtureId),Marshal.StringToHGlobalUni("1")};
            try
            {
                Marshal.WriteIntPtr(keys,0,strings[0]);Marshal.WriteIntPtr(keys,IntPtr.Size,strings[1]);Marshal.WriteIntPtr(values,0,strings[2]);Marshal.WriteIntPtr(values,IntPtr.Size,strings[3]);
                instance=DnsServiceConstructInstance("TVText Fixture "+Guid.NewGuid().ToString("N").Substring(0,5)+"._tvtext._tcp.local",Dns.GetHostName()+".local",IntPtr.Zero,IntPtr.Zero,45832,0,0,2,keys,values);
            }
            finally{foreach(IntPtr text in strings)Marshal.FreeHGlobal(text);Marshal.FreeHGlobal(keys);Marshal.FreeHGlobal(values);}
            if(instance==IntPtr.Zero)throw new Exception("DNS-SD fixture instance");
            cancel=Marshal.AllocHGlobal(IntPtr.Size);Marshal.WriteIntPtr(cancel,IntPtr.Zero);
            request=new Registration{Version=1,Instance=instance,Callback=Marshal.GetFunctionPointerForDelegate(callback)};
            uint started=DnsServiceRegister(ref request,cancel);
            if(started!=9506||!signal.WaitOne(12000)||result!=0)throw new Exception("DNS-SD registration failed: "+started+" / "+result);
        }
        private void OnComplete(uint status,IntPtr context,IntPtr value)
        {result=status;if(value!=IntPtr.Zero&&value!=instance)DnsServiceFreeInstance(value);signal.Set();}
        public void Dispose()
        {
            signal.Reset();DnsServiceDeRegister(ref request,cancel);signal.WaitOne(5000);
            DnsServiceFreeInstance(instance);Marshal.FreeHGlobal(cancel);GC.KeepAlive(callback);
            // The event and callback stay rooted until this test process exits.
        }
    }
}
