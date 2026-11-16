using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Threading;

namespace TvText
{
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
        { return new Connection { Endpoint = Endpoint, Fingerprint = Fingerprint, Token = Token, DeviceId = DeviceId, Name = Name }; }
        private bool SameVersion(Connection other)
        { return Endpoint == other.Endpoint && Fingerprint == other.Fingerprint && Token == other.Token && DeviceId == other.DeviceId && Name == other.Name; }
        public static Connection Load()
        {
            string active; List<Connection> all = ReadAll(out active);
            foreach (Connection item in all) if (item.DeviceId == active) return item;
            return all.Count > 0 ? all[0] : new Connection();
        }
        public static List<Connection> LoadAll() { string active; return ReadAll(out active); }
        private static List<Connection> ReadAll(out string active, bool strict = false)
        {
            active = ""; byte[] plain = null; LastReadError = "";
            try
            {
                if (!File.Exists(PathName)) return new List<Connection>();
                if (new FileInfo(PathName).Length > 1048576) throw new IOException("Invalid profile size");
                plain = ProtectedData.Unprotect(File.ReadAllBytes(PathName), Entropy, DataProtectionScope.CurrentUser);
                using (BinaryReader reader = new BinaryReader(new MemoryStream(plain), new UTF8Encoding(false, true)))
                {
                    string first = reader.ReadString();
                    if (first != Format)
                    {
                        Connection legacy = new Connection { Endpoint = first, Fingerprint = reader.ReadString(), Token = reader.ReadString() };
                        if (reader.BaseStream.Position < reader.BaseStream.Length) { legacy.DeviceId = reader.ReadString(); legacy.Name = reader.ReadString(); }
                        if (reader.BaseStream.Position != reader.BaseStream.Length) throw new IOException("Invalid legacy profile");
                        Validate(legacy);
                        legacy.persisted = legacy.Snapshot();
                        active = legacy.DeviceId; return new List<Connection> { legacy };
                    }
                    active = reader.ReadString(); int size = reader.ReadInt32();
                    if (size < 0 || size > 100) throw new IOException("Invalid profiles");
                    List<Connection> result = new List<Connection>();
                    HashSet<string> ids = new HashSet<string>();
                    for (int i = 0; i < size; i++)
                    {
                        Connection item = new Connection { Endpoint = reader.ReadString(), Fingerprint = reader.ReadString(), Token = reader.ReadString(), DeviceId = reader.ReadString(), Name = reader.ReadString() };
                        if (!ids.Add(item.DeviceId)) throw new IOException("Duplicate profile");
                        Validate(item);
                        item.persisted = item.Snapshot();
                        result.Add(item);
                    }
                    if (reader.BaseStream.Position != reader.BaseStream.Length) throw new IOException("Trailing profile data");
                    return result;
                }
            }
            catch (Exception e)
            {
                LastReadError = UiText.T("invalid_storage");
                if (strict) throw new IOException(UiText.T("invalid_storage"), e);
                return new List<Connection>();
            }
            finally { if (plain != null) Array.Clear(plain, 0, plain.Length); }
        }
        public void Save()
        {
            Validate(this);
            ChangeStore(delegate
            {
                string active; List<Connection> all = ReadAll(out active, true);
                int index = all.FindIndex(delegate (Connection item) { return item.DeviceId == DeviceId || item.DeviceId.Length == 0 && item.Endpoint == Endpoint; });
                if (persisted != null && (index < 0 || !all[index].SameVersion(persisted)))
                    throw new IOException(UiText.T("connection_changed"));
                if (index >= 0) all[index] = this;
                else { if (all.Count >= 100) throw new IOException(UiText.T("transport_too_many")); all.Add(this); }
                WriteAll(all, DeviceId);
                persisted = Snapshot();
            });
        }
        internal void SaveSelection()
        {
            ChangeStore(delegate
            {
                string active; var all = ReadAll(out active, true);
                if (!all.Exists(delegate (Connection item) { return item.DeviceId == DeviceId; }))
                    throw new IOException(UiText.T("connection_changed"));
                // Read-modify-write the active ID only; the stored profile may
                // already have newer credentials or an address from another window.
                if (active != DeviceId) WriteAll(all, DeviceId);
            });
        }
        private static void Validate(Connection item)
        {
            if (item.Endpoint == null || item.Fingerprint == null || item.Token == null || item.DeviceId == null || item.Name == null || item.Endpoint.Length > 256 || item.Fingerprint.Length > 128 || item.Token.Length > 256 || item.DeviceId.Length > 128 || item.Name.Length > 256) throw new IOException(UiText.T("invalid_data"));
            string host; int port; Client.ParseEndpoint(item.Endpoint, out host, out port);
            Guid id; if (item.DeviceId.Length > 0 && !Guid.TryParse(item.DeviceId, out id)) throw new IOException(UiText.T("transport_bad_id"));
        }
        private static void ChangeStore(Action change)
        {
            lock (StoreLock)
                using (Mutex mutex = new Mutex(false, "Local\\TVText.Store." + Client.Fingerprint(Encoding.UTF8.GetBytes(Path.GetFullPath(PathName).ToUpperInvariant()))))
                {
                    bool owned = false;
                    try
                    {
                        try { owned = mutex.WaitOne(3000); } catch (AbandonedMutexException) { owned = true; }
                        if (!owned) throw new IOException(UiText.T("transport_store_busy"));
                        change();
                    }
                    finally { if (owned) mutex.ReleaseMutex(); }
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
                    foreach (Connection item in all) { writer.Write(item.Endpoint); writer.Write(item.Fingerprint); writer.Write(item.Token); writer.Write(item.DeviceId); writer.Write(item.Name); }
                }
                plain = stream.ToArray();
            }
            byte[] encrypted;
            try { encrypted = ProtectedData.Protect(plain, Entropy, DataProtectionScope.CurrentUser); }
            finally { Array.Clear(plain, 0, plain.Length); }
            string file = PathName; Directory.CreateDirectory(Path.GetDirectoryName(file));
            string temporary = file + "." + Guid.NewGuid().ToString("N") + ".tmp";
            try
            {
                using (FileStream stream = new FileStream(temporary, FileMode.CreateNew, FileAccess.Write, FileShare.None)) { stream.Write(encrypted, 0, encrypted.Length); stream.Flush(true); }
                if (File.Exists(file)) File.Replace(temporary, file, null); else File.Move(temporary, file);
            }
            finally { if (File.Exists(temporary)) File.Delete(temporary); }
        }
        public static void Forget(string deviceId)
        {
            ChangeStore(delegate
            {
                string active; List<Connection> all = ReadAll(out active, true);
                all.RemoveAll(delegate (Connection item) { return item.DeviceId == deviceId; });
                if (active == deviceId) active = all.Count > 0 ? all[0].DeviceId : "";
                WriteAll(all, active);
            });
        }
    }

}
