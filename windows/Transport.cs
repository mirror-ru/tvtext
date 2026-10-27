using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace TvText
{
    public sealed class Reply
    {
        public int Code;
        public bool Ready;
        public string Message, Token, Target, DeviceId;
    }

    public static class Client
    {
        public const int Port = 45831, MaxText = 32768;
        internal static int OperationTimeoutMilliseconds = 12000;
        private static readonly UTF8Encoding Utf8 = new UTF8Encoding(false, true);
        public static string Fingerprint(byte[] cert)
        {
            using (SHA256 sha = SHA256.Create())
                return BitConverter.ToString(sha.ComputeHash(cert)).Replace("-", "");
        }
        public static string ShortFingerprint(string full)
        {
            return full.Substring(0, 4) + " " + full.Substring(4, 4) + " " + full.Substring(8, 4) + " " + full.Substring(12, 4);
        }
        public static void ParseEndpoint(string endpoint, out string host, out int port)
        {
            string value = (endpoint ?? "").Trim(), address = value, portText = null;
            if(value.StartsWith("[",StringComparison.Ordinal))
            {
                int end=value.IndexOf(']');
                if(end<0)throw new ArgumentException(UiText.T("transport_bad_endpoint"));
                address=value.Substring(1,end-1);
                if(end+1<value.Length)
                {
                    if(value[end+1]!=':')throw new ArgumentException(UiText.T("transport_bad_endpoint"));
                    portText=value.Substring(end+2);
                }
            }
            else if(value.IndexOf(':')>=0&&value.IndexOf(':')==value.LastIndexOf(':'))
            {int split=value.IndexOf(':');address=value.Substring(0,split);portText=value.Substring(split+1);}
            IPAddress ip;port=Port;
            if (!IPAddress.TryParse(address, out ip)
                || (portText != null && (!Int32.TryParse(portText, out port) || port < 1 || port > 65535)))
                throw new ArgumentException(UiText.T("transport_bad_endpoint_refresh"));
            host = ip.ToString();
        }
        internal static string FormatEndpoint(IPAddress address,int port)
        {return address.AddressFamily==AddressFamily.InterNetworkV6?"["+address+"]:"+port:address+":"+port;}
        private static T WithConnection<T>(string endpoint,CancellationToken cancellationToken,Func<TcpClient,T> action)
        {
            cancellationToken.ThrowIfCancellationRequested();
            string host; int port; ParseEndpoint(endpoint, out host, out port);
            using(CancellationTokenSource timeout=new CancellationTokenSource())
            using(CancellationTokenSource lifetime=CancellationTokenSource.CreateLinkedTokenSource(cancellationToken,timeout.Token))
            using(TcpClient tcp=new TcpClient(IPAddress.Parse(host).AddressFamily))
            using(CancellationTokenRegistration abort=lifetime.Token.Register(delegate{try{tcp.Close();}catch{}}))
            {
                timeout.CancelAfter(OperationTimeoutMilliseconds);
                try
                {
                    tcp.NoDelay=true;
                    IAsyncResult pending = tcp.BeginConnect(host, port, null, null);
                    using (WaitHandle wait = pending.AsyncWaitHandle)
                    {
                        if (!wait.WaitOne(4000)) throw new TimeoutException(UiText.T("transport_no_response"));
                        tcp.EndConnect(pending);
                    }
                    tcp.ReceiveTimeout=6000;tcp.SendTimeout=6000;
                    return action(tcp);
                }
                catch
                {
                    cancellationToken.ThrowIfCancellationRequested();
                    if(timeout.IsCancellationRequested)throw new TimeoutException(UiText.T("transport_timeout"));
                    throw;
                }
            }
        }
        public static string Probe(string endpoint,CancellationToken cancellationToken=default(CancellationToken))
        {
            // This connection sends no application payload and no pairing credentials.
            // The user compares its fingerprint on the TV before any secrets are sent.
            string fingerprint = null;
            return WithConnection(endpoint,cancellationToken,delegate(TcpClient tcp){
            using (SslStream tls = new SslStream(tcp.GetStream(), false, delegate(object s, System.Security.Cryptography.X509Certificates.X509Certificate c,
                System.Security.Cryptography.X509Certificates.X509Chain chain, SslPolicyErrors errors)
            {
                if (c == null) return false;
                fingerprint = Fingerprint(c.GetRawCertData()); return true;
            }))
            {
                tls.ReadTimeout = 6000; tls.WriteTimeout = 6000;
                tls.AuthenticateAsClient("TVText", null, SslProtocols.Tls12, false);
            }
            return fingerprint;
            });
        }
        public static Reply Exchange(string endpoint, string pin, int command, string credential, string id, string text,CancellationToken cancellationToken=default(CancellationToken))
        {
            if (String.IsNullOrEmpty(pin) || pin.Length != 64) throw new ArgumentException(UiText.T("pair_first"));
            if(Utf8.GetByteCount(credential)>256||Utf8.GetByteCount(id)>128)throw new ArgumentException(UiText.T("bad_request"));
            if (Utf8.GetByteCount(text) > MaxText) throw new ArgumentException(UiText.T("text_too_long_protocol"));
            bool pinRejected=false;
            return WithConnection(endpoint,cancellationToken,delegate(TcpClient tcp){
            using (SslStream tls = new SslStream(tcp.GetStream(), false, delegate(object s, System.Security.Cryptography.X509Certificates.X509Certificate c,
                System.Security.Cryptography.X509Certificates.X509Chain chain, SslPolicyErrors errors)
            { bool match=c != null && String.Equals(Fingerprint(c.GetRawCertData()), pin, StringComparison.Ordinal);if(!match)pinRejected=true;return match; }))
            {
                tls.ReadTimeout = 6000; tls.WriteTimeout = 6000;
                try{tls.AuthenticateAsClient("TVText", null, SslProtocols.Tls12, false);}
                catch(AuthenticationException e){if(pinRejected)throw;throw new IOException(UiText.T("transport_connect"),e);}
                using (MemoryStream payload = new MemoryStream())
                {
                    WriteInt(payload,0);
                    payload.WriteByte(1); payload.WriteByte((byte)command);
                    WriteString(payload, credential); WriteString(payload, id); WriteString(payload, text);
                    int size=checked((int)payload.Length-4);payload.Position=0;WriteInt(payload,size);
                    byte[] bytes = payload.ToArray();tls.Write(bytes, 0, bytes.Length); tls.Flush();
                }
                return ReadReply(tls);
            }
            });
        }
        internal static Reply ReadReply(Stream stream)
        {
                int size = ReadInt(stream);
                if (size < 3 || size > 65536) throw new IOException(UiText.T("transport_bad_reply"));
                using (MemoryStream response = new MemoryStream(ReadExact(stream, size)))
                {
                    if (response.ReadByte() != 1) throw new IOException(UiText.T("transport_incompatible"));
                    Reply reply = new Reply();reply.Code=response.ReadByte();int ready=response.ReadByte();
                    if(ready<0||ready>1||reply.Code<0||reply.Code>6&&reply.Code!=9)throw new IOException(UiText.T("transport_bad_reply"));
                    reply.Ready=ready==1;
                    reply.Message = ReadString(response, 4096); reply.Token = ReadString(response, 256); reply.Target = ReadString(response, 1024); reply.DeviceId = ReadString(response, 128);
                    if (response.Position != response.Length) throw new IOException(UiText.T("transport_bad_reply"));
                    return reply;
                }
        }
        internal static void VerifyIdentity(Reply reply,Connection saved)
        {if((reply.Code==0||reply.Code==1)&&saved.DeviceId.Length>0&&reply.DeviceId!=saved.DeviceId)throw new AuthenticationException(UiText.T("transport_changed_id"));}
        private static void WriteInt(Stream stream, int value)
        { byte[] b = { (byte)(value >> 24), (byte)(value >> 16), (byte)(value >> 8), (byte)value }; stream.Write(b, 0, 4); }
        private static int ReadInt(Stream stream)
        { byte[] b = ReadExact(stream, 4); return (b[0] << 24) | (b[1] << 16) | (b[2] << 8) | b[3]; }
        private static byte[] ReadExact(Stream stream, int size)
        {
            byte[] result = new byte[size]; int offset = 0;
            while (offset < size) { int count = stream.Read(result, offset, size - offset); if (count <= 0) throw new EndOfStreamException(UiText.T("transport_closed")); offset += count; }
            return result;
        }
        private static void WriteString(Stream stream, string value)
        { byte[] bytes = Utf8.GetBytes(value); WriteInt(stream, bytes.Length); stream.Write(bytes, 0, bytes.Length); }
        private static string ReadString(Stream stream, int limit)
        { int size = ReadInt(stream); if (size < 0 || size > limit) throw new IOException(UiText.T("bad_reply_size")); return Utf8.GetString(ReadExact(stream, size)); }
    }

    public sealed class Connection
    {
        public string Endpoint = "", Fingerprint = "", Token = "", DeviceId = "", Name = "Мой телевизор";
        internal static string StoragePathOverride { get; set; }
        private static string lastReadError = "";
        internal static string LastReadError { get { return lastReadError; } private set { lastReadError = value; } }
        private static string PathName { get { return StoragePathOverride ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "TVText", "connection.dat"); } }
        private static readonly byte[] Entropy = Encoding.UTF8.GetBytes("TVText.Local.v1");
        private static readonly object StoreLock = new object();
        private const string Format = "TVText-Profiles-2";
        private Connection persisted;
        private Connection Snapshot()
        {return new Connection{Endpoint=Endpoint,Fingerprint=Fingerprint,Token=Token,DeviceId=DeviceId,Name=Name};}
        private bool SameVersion(Connection other)
        {return Endpoint==other.Endpoint&&Fingerprint==other.Fingerprint&&Token==other.Token&&DeviceId==other.DeviceId&&Name==other.Name;}
        public static Connection Load()
        {
            string active; List<Connection> all = ReadAll(out active);
            foreach(Connection item in all) if(item.DeviceId == active) return item;
            return all.Count > 0 ? all[0] : new Connection();
        }
        public static List<Connection> LoadAll() { string active; return ReadAll(out active); }
        private static List<Connection> ReadAll(out string active,bool strict=false)
        {
            active = "";byte[] plain=null;LastReadError="";
            try
            {
                if(!File.Exists(PathName))return new List<Connection>();
                if(new FileInfo(PathName).Length>1048576)throw new IOException("Invalid profile size");
                plain = ProtectedData.Unprotect(File.ReadAllBytes(PathName), Entropy, DataProtectionScope.CurrentUser);
                using (BinaryReader reader = new BinaryReader(new MemoryStream(plain),new UTF8Encoding(false,true)))
                {
                    string first = reader.ReadString();
                    if(first != Format)
                    {
                        Connection legacy = new Connection { Endpoint = first, Fingerprint = reader.ReadString(), Token = reader.ReadString() };
                        if(reader.BaseStream.Position < reader.BaseStream.Length) { legacy.DeviceId = reader.ReadString(); legacy.Name = reader.ReadString(); }
                        if(reader.BaseStream.Position!=reader.BaseStream.Length)throw new IOException("Invalid legacy profile");
                        Validate(legacy);
                        legacy.persisted=legacy.Snapshot();
                        active = legacy.DeviceId; return new List<Connection> { legacy };
                    }
                    active = reader.ReadString(); int size = reader.ReadInt32();
                    if(size < 0 || size > 100) throw new IOException("Invalid profiles");
                    List<Connection> result = new List<Connection>();
                    HashSet<string> ids=new HashSet<string>();
                    for(int i = 0; i < size; i++)
                    {
                        Connection item=new Connection { Endpoint = reader.ReadString(), Fingerprint = reader.ReadString(), Token = reader.ReadString(), DeviceId = reader.ReadString(), Name = reader.ReadString() };
                        if(!ids.Add(item.DeviceId))throw new IOException("Duplicate profile");
                        Validate(item);
                        item.persisted=item.Snapshot();
                        result.Add(item);
                    }
                    if(reader.BaseStream.Position!=reader.BaseStream.Length)throw new IOException("Trailing profile data");
                    return result;
                }
            }
            catch(Exception e)
            {
                LastReadError=UiText.T("invalid_storage");
                if(strict)throw new IOException(UiText.T("invalid_storage"),e);
                return new List<Connection>();
            }
            finally{if(plain!=null)Array.Clear(plain,0,plain.Length);}
        }
        public void Save()
        {
            Validate(this);
            ChangeStore(delegate
            {
                string active; List<Connection> all = ReadAll(out active,true);
                int index=all.FindIndex(delegate(Connection item){return item.DeviceId==DeviceId||item.DeviceId.Length==0&&item.Endpoint==Endpoint;});
                if(persisted!=null&&(index<0||!all[index].SameVersion(persisted)))
                    throw new IOException(UiText.T("connection_changed"));
                if(index>=0)all[index]=this;
                else {if(all.Count>=100)throw new IOException(UiText.T("transport_too_many"));all.Add(this);}
                WriteAll(all, DeviceId);
                persisted=Snapshot();
            });
        }
        internal void SaveSelection()
        {
            ChangeStore(delegate
            {
                string active;var all=ReadAll(out active,true);
                if(!all.Exists(delegate(Connection item){return item.DeviceId==DeviceId;}))
                    throw new IOException(UiText.T("connection_changed"));
                // Read-modify-write the active ID only; the stored profile may
                // already have newer credentials or an address from another window.
                if(active!=DeviceId)WriteAll(all,DeviceId);
            });
        }
        private static void Validate(Connection item)
        {
            if(item.Endpoint==null||item.Fingerprint==null||item.Token==null||item.DeviceId==null||item.Name==null||item.Endpoint.Length>256||item.Fingerprint.Length>128||item.Token.Length>256||item.DeviceId.Length>128||item.Name.Length>256)throw new IOException(UiText.T("invalid_data"));
            string host;int port;Client.ParseEndpoint(item.Endpoint,out host,out port);
            Guid id;if(item.DeviceId.Length>0&&!Guid.TryParse(item.DeviceId,out id))throw new IOException(UiText.T("transport_bad_id"));
        }
        private static void ChangeStore(Action change)
        {
            lock(StoreLock)
            using(Mutex mutex=new Mutex(false,"Local\\TVText.Store."+Client.Fingerprint(Encoding.UTF8.GetBytes(Path.GetFullPath(PathName).ToUpperInvariant()))))
            {
                bool owned=false;
                try
                {
                    try{owned=mutex.WaitOne(3000);}catch(AbandonedMutexException){owned=true;}
                    if(!owned)throw new IOException(UiText.T("transport_store_busy"));
                    change();
                }
                finally{if(owned)mutex.ReleaseMutex();}
            }
        }
        private static void WriteAll(List<Connection> all, string active)
        {
            byte[] plain;
            using (MemoryStream stream = new MemoryStream())
            {
                using (BinaryWriter writer = new BinaryWriter(stream, Encoding.UTF8, true))
                {
                    writer.Write(Format); writer.Write(active); writer.Write(all.Count);
                    foreach(Connection item in all) { writer.Write(item.Endpoint); writer.Write(item.Fingerprint); writer.Write(item.Token); writer.Write(item.DeviceId); writer.Write(item.Name); }
                }
                plain = stream.ToArray();
            }
            byte[] encrypted;
            try{encrypted=ProtectedData.Protect(plain, Entropy, DataProtectionScope.CurrentUser);}
            finally{Array.Clear(plain,0,plain.Length);}
            string file = PathName; Directory.CreateDirectory(Path.GetDirectoryName(file));
            string temporary=file+"."+Guid.NewGuid().ToString("N")+".tmp";
            try
            {
                using(FileStream stream=new FileStream(temporary,FileMode.CreateNew,FileAccess.Write,FileShare.None)){stream.Write(encrypted,0,encrypted.Length);stream.Flush(true);}
                if (File.Exists(file)) File.Replace(temporary, file, null); else File.Move(temporary, file);
            }
            finally{if(File.Exists(temporary))File.Delete(temporary);}
        }
        public static void Forget(string deviceId)
        {
            ChangeStore(delegate
            {
                string active; List<Connection> all = ReadAll(out active,true);
                all.RemoveAll(delegate(Connection item) { return item.DeviceId == deviceId; });
                if(active == deviceId) active = all.Count > 0 ? all[0].DeviceId : "";
                WriteAll(all, active);
            });
        }
    }

    internal static class ConnectionResolver
    {
        internal static async Task<DiscoveredTv> FindVerifiedAsync(Connection saved, IEnumerable<DiscoveredTv> candidates,CancellationToken cancellationToken=default(CancellationToken))
        {
            using(CancellationTokenSource budget=CancellationTokenSource.CreateLinkedTokenSource(cancellationToken))
            {
            budget.CancelAfter(8000);
            foreach(DiscoveredTv tv in candidates)
            {
                if(saved.DeviceId.Length == 0 || tv.DeviceId != saved.DeviceId) continue;
                try
                {
                    budget.Token.ThrowIfCancellationRequested();
                    Reply reply = await Task.Run(delegate { return Client.Exchange(tv.Endpoint, saved.Fingerprint, 2, saved.Token, "", "",budget.Token); });
                    if(reply.Code == 0 && reply.DeviceId == saved.DeviceId) return tv;
                }
                catch(OperationCanceledException){cancellationToken.ThrowIfCancellationRequested();return null;}
                catch { }
            }
            return null;
            }
        }
    }


}
