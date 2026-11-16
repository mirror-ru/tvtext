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
    internal enum NetworkStage { Connect, Tls, Response }
    internal sealed class NetworkFailure : IOException
    {
        internal readonly NetworkStage Stage;
        internal NetworkFailure(NetworkStage stage, Exception cause) : base("TVText network stage failed", cause) { Stage = stage; }
    }
    internal sealed class ProtocolResponseFailure : IOException
    { internal ProtocolResponseFailure(string message) : base(message) { } }

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
            if (value.StartsWith("[", StringComparison.Ordinal))
            {
                int end = value.IndexOf(']');
                if (end < 0) throw new ArgumentException(UiText.T("transport_bad_endpoint"));
                address = value.Substring(1, end - 1);
                if (end + 1 < value.Length)
                {
                    if (value[end + 1] != ':') throw new ArgumentException(UiText.T("transport_bad_endpoint"));
                    portText = value.Substring(end + 2);
                }
            }
            else if (value.IndexOf(':') >= 0 && value.IndexOf(':') == value.LastIndexOf(':'))
            { int split = value.IndexOf(':'); address = value.Substring(0, split); portText = value.Substring(split + 1); }
            IPAddress ip; port = Port;
            if (!IPAddress.TryParse(address, out ip)
                || (portText != null && (!Int32.TryParse(portText, out port) || port < 1 || port > 65535)))
                throw new ArgumentException(UiText.T("transport_bad_endpoint_refresh"));
            host = ip.ToString();
        }
        internal static string FormatEndpoint(IPAddress address, int port)
        { return address.AddressFamily == AddressFamily.InterNetworkV6 ? "[" + address + "]:" + port : address + ":" + port; }
        private static T WithConnection<T>(string endpoint, CancellationToken cancellationToken, Func<TcpClient, T> action)
        {
            cancellationToken.ThrowIfCancellationRequested();
            string host; int port; ParseEndpoint(endpoint, out host, out port);
            using (CancellationTokenSource timeout = new CancellationTokenSource())
            using (CancellationTokenSource lifetime = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken, timeout.Token))
            using (TcpClient tcp = new TcpClient(IPAddress.Parse(host).AddressFamily))
            using (CancellationTokenRegistration abort = lifetime.Token.Register(delegate { try { tcp.Close(); } catch { } }))
            {
                timeout.CancelAfter(OperationTimeoutMilliseconds);
                try
                {
                    tcp.NoDelay = true;
                    try
                    {
                        IAsyncResult pending = tcp.BeginConnect(host, port, null, null);
                        using (WaitHandle wait = pending.AsyncWaitHandle)
                        {
                            if (!wait.WaitOne(4000)) throw new TimeoutException(UiText.T("transport_no_response"));
                            tcp.EndConnect(pending);
                        }
                    }
                    catch (SocketException error) { throw new NetworkFailure(NetworkStage.Connect, error); }
                    tcp.ReceiveTimeout = 6000; tcp.SendTimeout = 6000;
                    return action(tcp);
                }
                catch
                {
                    cancellationToken.ThrowIfCancellationRequested();
                    if (timeout.IsCancellationRequested) throw new TimeoutException(UiText.T("transport_timeout"));
                    throw;
                }
            }
        }
        public static string Probe(string endpoint, CancellationToken cancellationToken = default(CancellationToken))
        {
            // This connection sends no application payload and no pairing credentials.
            // The user compares its fingerprint on the TV before any secrets are sent.
            string fingerprint = null;
            return WithConnection(endpoint, cancellationToken, delegate (TcpClient tcp)
            {
                using (SslStream tls = new SslStream(tcp.GetStream(), false, delegate (object s, System.Security.Cryptography.X509Certificates.X509Certificate c,
                    System.Security.Cryptography.X509Certificates.X509Chain chain, SslPolicyErrors errors)
                {
                    if (c == null) return false;
                    fingerprint = Fingerprint(c.GetRawCertData()); return true;
                }))
                {
                    tls.ReadTimeout = 6000; tls.WriteTimeout = 6000;
                    try { tls.AuthenticateAsClient("TVText", null, SslProtocols.Tls12, false); }
                    catch (AuthenticationException error) { throw new NetworkFailure(NetworkStage.Tls, error); }
                }
                return fingerprint;
            });
        }
        public static Reply Exchange(string endpoint, string pin, int command, string credential, string id, string text, CancellationToken cancellationToken = default(CancellationToken))
        {
            if (String.IsNullOrEmpty(pin) || pin.Length != 64) throw new ArgumentException(UiText.T("pair_first"));
            ValidateRequest(command, credential, id, text);
            bool pinRejected = false;
            return WithConnection(endpoint, cancellationToken, delegate (TcpClient tcp)
            {
                using (SslStream tls = new SslStream(tcp.GetStream(), false, delegate (object s, System.Security.Cryptography.X509Certificates.X509Certificate c,
                    System.Security.Cryptography.X509Certificates.X509Chain chain, SslPolicyErrors errors)
                { bool match = c != null && String.Equals(Fingerprint(c.GetRawCertData()), pin, StringComparison.Ordinal); if (!match) pinRejected = true; return match; }))
                {
                    tls.ReadTimeout = 6000; tls.WriteTimeout = 6000;
                    try { tls.AuthenticateAsClient("TVText", null, SslProtocols.Tls12, false); }
                    catch (AuthenticationException e) { if (pinRejected) throw; throw new NetworkFailure(NetworkStage.Tls, e); }
                    using (MemoryStream payload = new MemoryStream())
                    {
                        byte[] bytes = EncodeRequest(command, credential, id, text);
                        tls.Write(bytes, 0, bytes.Length); tls.Flush();
                    }
                    try { return ReadReply(tls); }
                    catch (ProtocolResponseFailure) { throw; }
                    catch (IOException error) { throw new NetworkFailure(NetworkStage.Response, error); }
                }
            });
        }
        internal static byte[] EncodeRequest(int command, string credential, string id, string text)
        {
            ValidateRequest(command, credential, id, text);
            using (MemoryStream payload = new MemoryStream())
            {
                WriteInt(payload, 0);
                payload.WriteByte(1); payload.WriteByte((byte)command);
                WriteString(payload, credential); WriteString(payload, id); WriteString(payload, text);
                int size = checked((int)payload.Length - 4); payload.Position = 0; WriteInt(payload, size);
                return payload.ToArray();
            }
        }
        private static void ValidateRequest(int command, string credential, string id, string text)
        {
            if (command < 1 || command > 4) throw new ArgumentOutOfRangeException("command");
            if (Utf8.GetByteCount(credential) > 256 || Utf8.GetByteCount(id) > 128) throw new ArgumentException(UiText.T("bad_request"));
            if (Utf8.GetByteCount(text) > MaxText) throw new ArgumentException(UiText.T("text_too_long_protocol"));
        }
        internal static Reply ReadReply(Stream stream)
        {
            int size = ReadInt(stream);
            if (size < 3 || size > 65536) throw new ProtocolResponseFailure(UiText.T("transport_bad_reply"));
            using (MemoryStream response = new MemoryStream(ReadExact(stream, size)))
            {
                if (response.ReadByte() != 1) throw new ProtocolResponseFailure(UiText.T("transport_incompatible"));
                Reply reply = new Reply(); reply.Code = response.ReadByte(); int ready = response.ReadByte();
                if (ready < 0 || ready > 1 || reply.Code < 0 || reply.Code > 6 && reply.Code != 9) throw new ProtocolResponseFailure(UiText.T("transport_bad_reply"));
                reply.Ready = ready == 1;
                reply.Message = ReadString(response, 4096); reply.Token = ReadString(response, 256); reply.Target = ReadString(response, 1024); reply.DeviceId = ReadString(response, 128);
                if (response.Position != response.Length) throw new ProtocolResponseFailure(UiText.T("transport_bad_reply"));
                return reply;
            }
        }
        internal static void VerifyIdentity(Reply reply, Connection saved)
        { if ((reply.Code == 0 || reply.Code == 1) && saved.DeviceId.Length > 0 && reply.DeviceId != saved.DeviceId) throw new AuthenticationException(UiText.T("transport_changed_id")); }
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
        { int size = ReadInt(stream); if (size < 0 || size > limit) throw new ProtocolResponseFailure(UiText.T("bad_reply_size")); try { return Utf8.GetString(ReadExact(stream, size)); } catch (DecoderFallbackException) { throw new ProtocolResponseFailure(UiText.T("transport_bad_reply")); } }
    }

}
