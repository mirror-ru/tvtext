using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Threading;

namespace TvText
{
    // Names belong to this PC and are independent of pairing keys and mDNS names.
    internal static class DeviceNames
    {
        internal const int MaxNameLength=80;
        internal static string StoragePathOverride{get;set;}
        private static readonly object gate=new object();
        private static readonly UTF8Encoding utf8=new UTF8Encoding(false,true);
        private static string PathName{get{return StoragePathOverride??Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"TVText","device-names.dat");}}
        internal static bool ValidName(string name)
        {
            if(String.IsNullOrWhiteSpace(name)||name.Trim().Length>MaxNameLength)return false;
            foreach(char value in name)if(Char.IsControl(value))return false;
            try{utf8.GetByteCount(name);return true;}catch(EncoderFallbackException){return false;}
        }
        internal static Dictionary<string,string> Load()
        {
            try{return ReadAll();}catch{return new Dictionary<string,string>(StringComparer.OrdinalIgnoreCase);}
        }
        private static Dictionary<string,string> ReadAll()
        {
            Dictionary<string,string> names=new Dictionary<string,string>(StringComparer.OrdinalIgnoreCase);
            if(!File.Exists(PathName))return names;
            if(new FileInfo(PathName).Length>131072)throw new IOException(UiText.T("invalid_names"));
            using(BinaryReader reader=new BinaryReader(new MemoryStream(File.ReadAllBytes(PathName)),utf8))
            {
                if(reader.ReadString()!="TVText-Names-1")throw new IOException(UiText.T("invalid_names"));
                int count=reader.ReadInt32();
                if(count<0||count>256)throw new IOException(UiText.T("invalid_names"));
                for(int i=0;i<count;i++)
                {
                    string id=reader.ReadString(),name=reader.ReadString();Guid parsed;
                    if(!Guid.TryParse(id,out parsed)||!ValidName(name)||names.ContainsKey(parsed.ToString()))throw new IOException(UiText.T("invalid_names"));
                    names.Add(parsed.ToString(),name);
                }
                if(reader.BaseStream.Position!=reader.BaseStream.Length)throw new IOException(UiText.T("invalid_names"));
            }
            return names;
        }
        internal static Dictionary<string,string> Set(string deviceId,string name)
        {
            Guid id;
            if(!Guid.TryParse(deviceId,out id)||!ValidName(name))throw new ArgumentException(UiText.T("name_required"));
            name=name.Trim();
            lock(gate)
            using(Mutex mutex=new Mutex(false,"Local\\TVText.Names."+Client.Fingerprint(Encoding.UTF8.GetBytes(Path.GetFullPath(PathName).ToUpperInvariant()))))
            {
                bool owned=false;
                try
                {
                    try{owned=mutex.WaitOne(3000);}catch(AbandonedMutexException){owned=true;}
                    if(!owned)throw new IOException(UiText.T("transport_store_busy"));
                    Dictionary<string,string> names=ReadAll();
                    if(names.Count>=256&&!names.ContainsKey(id.ToString()))throw new IOException(UiText.T("too_many_names"));
                    names[id.ToString()]=name;
                    string path=PathName;Directory.CreateDirectory(Path.GetDirectoryName(path));
                    string temporary=path+"."+Guid.NewGuid().ToString("N")+".tmp";
                    try
                    {
                        using(FileStream stream=new FileStream(temporary,FileMode.CreateNew,FileAccess.Write,FileShare.None))
                        {
                            using(BinaryWriter writer=new BinaryWriter(stream,utf8,true))
                            {
                                writer.Write("TVText-Names-1");writer.Write(names.Count);
                                foreach(KeyValuePair<string,string> item in names){writer.Write(item.Key);writer.Write(item.Value);}
                            }
                            stream.Flush(true);
                        }
                        if(File.Exists(path))File.Replace(temporary,path,null);else File.Move(temporary,path);
                    }
                    finally{if(File.Exists(temporary))File.Delete(temporary);}
                    return names;
                }
                finally{if(owned)mutex.ReleaseMutex();}
            }
        }
    }
}
