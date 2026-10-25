using System;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using TvText;

internal static class ClientReliabilityTests
{
    private static void Check(bool value,string message){if(!value)throw new Exception(message);}
    internal static void Run(string folder)
    {
        Endpoints();Frames();Profiles(folder);Timeouts();DiscoveryCancellation();
    }
    internal static int ProfileWriter(string file)
    {
        Connection.StoragePathOverride=file;
        for(int i=0;i<8;i++)new Connection{DeviceId=Guid.NewGuid().ToString(),Name="Тест "+i,Endpoint="127.0.0.1:45832",Token="fixture",Fingerprint=new string('A',64)}.Save();
        return 0;
    }
    private static void Endpoints()
    {
        string host;int port;
        Client.ParseEndpoint("192.168.1.5:1234",out host,out port);Check(host=="192.168.1.5"&&port==1234,"IPv4 endpoint");
        Client.ParseEndpoint("[::1]:45832",out host,out port);Check(IPAddress.Parse(host).Equals(IPAddress.IPv6Loopback)&&port==45832,"IPv6 endpoint");
        Client.ParseEndpoint("[fe80::abcd%12]:45831",out host,out port);Check(IPAddress.Parse(host).ScopeId==12,"IPv6 link-local scope");
        Client.ParseEndpoint("::1",out host,out port);Check(port==45831,"bare IPv6 default port");
        foreach(string invalid in new[]{"","[::1","[::1]oops","[::1]:0","127.0.0.1:70000","hostname.local"})
        {bool rejected=false;try{Client.ParseEndpoint(invalid,out host,out port);}catch(ArgumentException){rejected=true;}Check(rejected,"invalid endpoint accepted: "+invalid);}
        NativeIpv6();
        Console.WriteLine("PASS: IPv4/IPv6 parsing and native DNS-SD IPv6 address with interface scope");
    }
    [StructLayout(LayoutKind.Sequential)]private struct Instance
    {public IntPtr Name,Host,Ip4,Ip6;public ushort Port,Priority,Weight;public uint Count;public IntPtr Keys,Values;public uint Interface;}
    private static void NativeIpv6()
    {
        IntPtr name=Marshal.StringToHGlobalUni("Fixture._tvtext._tcp.local"),key=Marshal.StringToHGlobalUni("id"),version=Marshal.StringToHGlobalUni("v"),flag=Marshal.StringToHGlobalUni("ipv4-only");
        IntPtr id=Marshal.StringToHGlobalUni(DiscoveryTests.FixtureId),one=Marshal.StringToHGlobalUni("1");
        IntPtr keys=Marshal.AllocHGlobal(IntPtr.Size*3),values=Marshal.AllocHGlobal(IntPtr.Size*3),ip=Marshal.AllocHGlobal(16),ip4=Marshal.AllocHGlobal(4),record=Marshal.AllocHGlobal(Marshal.SizeOf(typeof(Instance)));
        try
        {
            Marshal.WriteIntPtr(keys,0,key);Marshal.WriteIntPtr(keys,IntPtr.Size,version);Marshal.WriteIntPtr(values,0,id);Marshal.WriteIntPtr(values,IntPtr.Size,one);
            byte[] bytes=IPAddress.Parse("fe80::abcd").GetAddressBytes();Marshal.Copy(bytes,0,ip,16);
            Instance value=new Instance{Name=name,Ip6=ip,Port=45831,Count=2,Keys=keys,Values=values,Interface=12};
            Marshal.StructureToPtr(value,record,false);DiscoveredTv tv=Discovery.ReadInstance(record);
            Check(tv!=null&&tv.Endpoint=="[fe80::abcd%12]:45831"&&tv.DeviceId==DiscoveryTests.FixtureId,"native IPv6 instance");
            Marshal.WriteIntPtr(keys,IntPtr.Size*2,flag);Marshal.WriteIntPtr(values,IntPtr.Size*2,one);value.Count=3;
            Marshal.StructureToPtr(value,record,false);Check(Discovery.ReadInstance(record)==null,"IPv4-only advertisement must not resolve to IPv6");
            Marshal.Copy(new byte[]{127,0,0,1},0,ip4,4);value.Ip4=ip4;Marshal.StructureToPtr(value,record,false);
            Check(Discovery.ReadInstance(record).Endpoint=="127.0.0.1:45831","IPv4-only advertisement selects IPv4");
            value.Count=65;Marshal.StructureToPtr(value,record,false);Check(Discovery.ReadInstance(record)==null,"oversized TXT metadata rejected");
        }
        finally{foreach(IntPtr pointer in new[]{name,key,version,flag,id,one,keys,values,ip,ip4,record})Marshal.FreeHGlobal(pointer);}
    }
    private static byte[] Frame(byte[] body)
    {byte[] bytes=new byte[body.Length+4];int n=body.Length;bytes[0]=(byte)(n>>24);bytes[1]=(byte)(n>>16);bytes[2]=(byte)(n>>8);bytes[3]=(byte)n;Buffer.BlockCopy(body,0,bytes,4,n);return bytes;}
    private static void Frames()
    {
        byte[] body=new byte[19];body[0]=1; // version, result, ready, four empty strings
        Check(Client.ReadReply(new MemoryStream(Frame(body))).Code==0,"valid empty reply");
        foreach(int field in new[]{0,1,2})
        {
            byte[] invalid=(byte[])body.Clone();invalid[field]=(byte)(field==0?2:field==1?8:2);
            bool rejected=false;try{Client.ReadReply(new MemoryStream(Frame(invalid)));}catch(IOException){rejected=true;}Check(rejected,"malformed reply header");
        }
        foreach(byte[] invalid in new[]{new byte[]{255,255,255,255},new byte[]{0,0,0,19,1,0},Frame(new byte[]{1,0,0,255,255,255,255}),Frame(new byte[20])})
        {bool rejected=false;try{Client.ReadReply(new MemoryStream(invalid));}catch(IOException){rejected=true;}Check(rejected,"malformed frame must fail");}
        bool badUnicode=false;try{Client.Exchange("127.0.0.1:1",new string('A',64),3,"token",Guid.NewGuid().ToString(),"\ud800");}catch(EncoderFallbackException){badUnicode=true;}Check(badUnicode,"invalid UTF-16 rejected before connection");
        bool wrongId=false;try{Client.VerifyIdentity(new Reply{Code=0,DeviceId=Guid.NewGuid().ToString()},new Connection{DeviceId=DiscoveryTests.FixtureId});}catch(System.Security.Authentication.AuthenticationException){wrongId=true;}Check(wrongId,"reply identity must match saved TV");
        Console.WriteLine("PASS: malformed replies, invalid UTF-16 and mismatched TV identity rejected");
    }
    private static void Profiles(string folder)
    {
        string file=Path.Combine(folder,"reliability-profiles.dat");Connection.StoragePathOverride=file;
        try
        {
            Connection a=new Connection{DeviceId=Guid.NewGuid().ToString(),Name="A",Endpoint="127.0.0.1:45832",Token="A"};
            Connection b=new Connection{DeviceId=Guid.NewGuid().ToString(),Name="B",Endpoint="127.0.0.1:45833",Token="B"};
            a.Save();b.Save();a.Save();Check(Connection.LoadAll()[0].DeviceId==a.DeviceId,"switching preserves profile order");
            File.WriteAllBytes(file,new byte[]{1,2,3,4});bool rejected=false;
            try{a.Save();}catch(IOException){rejected=true;}
            Check(rejected&&BitConverter.ToString(File.ReadAllBytes(file))=="01-02-03-04","corrupt profile must not be overwritten");
        }
        finally{Connection.StoragePathOverride=null;}
        string parallel=Path.Combine(folder,"parallel-profiles.dat");
        ProcessStartInfo start=new ProcessStartInfo(System.Reflection.Assembly.GetExecutingAssembly().Location,"profile-writer \""+parallel+"\""){UseShellExecute=false,CreateNoWindow=true,WindowStyle=ProcessWindowStyle.Hidden};
        using(Process first=Process.Start(start))using(Process second=Process.Start(start))
        {Check(first.WaitForExit(15000)&&second.WaitForExit(15000)&&first.ExitCode==0&&second.ExitCode==0,"parallel profile writers");}
        Connection.StoragePathOverride=parallel;
        try{Check(Connection.LoadAll().Count==16,"parallel instances must retain every TV profile");}
        finally{Connection.StoragePathOverride=null;}
        Check(Directory.GetFiles(folder,"*.tmp").Length==0,"no leftover temporary credential files");
        Console.WriteLine("PASS: stable profile order, corrupt file preservation, two concurrent processes retain all 16 encrypted profiles");
    }
    private static void Timeouts()
    {
        int original=Client.OperationTimeoutMilliseconds;Client.OperationTimeoutMilliseconds=250;
        TcpListener listener=new TcpListener(IPAddress.Loopback,0);listener.Start();
        try
        {
            string endpoint=Client.FormatEndpoint(IPAddress.Loopback,((IPEndPoint)listener.LocalEndpoint).Port);
            Task<TcpClient> accepted=listener.AcceptTcpClientAsync();Stopwatch watch=Stopwatch.StartNew();bool timedOut=false;
            try{Client.Probe(endpoint);}catch(TimeoutException){timedOut=true;}
            using(TcpClient socket=accepted.GetAwaiter().GetResult()){}
            Check(timedOut&&watch.ElapsedMilliseconds<2000,"overall TLS handshake deadline");
            accepted=listener.AcceptTcpClientAsync();watch.Restart();bool cancelled=false;
            using(CancellationTokenSource cancel=new CancellationTokenSource(100))
            {try{Client.Exchange(endpoint,new string('A',64),2,"fixture","","",cancel.Token);}catch(OperationCanceledException){cancelled=true;}}
            using(TcpClient socket=accepted.GetAwaiter().GetResult()){}
            Check(cancelled&&watch.ElapsedMilliseconds<2000,"cancel in-flight TLS handshake");
        }
        finally{listener.Stop();Client.OperationTimeoutMilliseconds=original;}
        Console.WriteLine("PASS: total TLS deadline and in-flight cancellation close a stalled connection promptly");
    }
    private static void DiscoveryCancellation()
    {
        for(int i=0;i<4;i++)
        {
            Stopwatch watch=Stopwatch.StartNew();bool cancelled=false;
            using(CancellationTokenSource cancel=new CancellationTokenSource(100))
            {try{Discovery.FindAsync(30000,cancel.Token).GetAwaiter().GetResult();}catch(OperationCanceledException){cancelled=true;}}
            Check(cancelled&&watch.ElapsedMilliseconds<2000,"native discovery cancellation");
        }
        DateTime end=DateTime.UtcNow.AddSeconds(3);
        while(Discovery.PendingOperations!=0&&DateTime.UtcNow<end)Thread.Sleep(20);
        Check(Discovery.PendingOperations==0,"native discovery requests/callback roots must be released");
        Console.WriteLine("PASS: four cancelled mDNS searches release all native handles and callback roots");
    }
}
