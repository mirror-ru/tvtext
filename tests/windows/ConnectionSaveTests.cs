using System;
using System.IO;
using System.Reflection;
using System.Windows.Forms;
using TvText;

internal static class ConnectionSaveTests
{
    internal static void Run()
    {
        string previous=Connection.StoragePathOverride;
        string directory=Path.Combine(Path.GetTempPath(),"TVText-save-test-"+Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(directory);
        try
        {
            Connection saved=new Connection{DeviceId=Guid.NewGuid().ToString(),Name="TV",Endpoint="127.0.0.1:45831",Token=new string('a',64),Fingerprint=new string('b',64)};
            Connection.StoragePathOverride=Path.Combine(directory,"connections.dat");saved.Save();
            byte[] original=File.ReadAllBytes(Connection.StoragePathOverride);
            using(MainForm form=new MainForm(saved,true,new[]{saved}))
            {
                TextBox input=(TextBox)form.Controls.Find("TextBuffer",true)[0];input.Text="Keep draft";
                TvDeviceList list=(TvDeviceList)form.Controls.Find("DeviceList",true)[0];
                MethodInfo apply=typeof(MainForm).GetMethod("ApplyEndpoint",BindingFlags.Instance|BindingFlags.NonPublic);
                // A file where a parent directory is expected reliably denies a write.
                Connection.StoragePathOverride=Path.Combine(Connection.StoragePathOverride,"blocked.dat");
                Check(!(bool)apply.Invoke(form,new object[]{saved,"127.0.0.2:45831"}),"write failure must be reported");
                Check(saved.Endpoint=="127.0.0.2:45831","verified address remains usable in this session");
                typeof(MainForm).GetMethod("ClearStatus",BindingFlags.Instance|BindingFlags.NonPublic).Invoke(form,null);
                Check(list.DeviceCaption((DiscoveredTv)list.Items[0])==UiText.T("save_failed"),"clear status must not hide an unsaved connection");
                Check(input.Text=="Keep draft"&&form.Controls.Find("SendButton",true)[0].Enabled,"save error preserves draft and usable connection");
                Connection.StoragePathOverride=Path.Combine(directory,"connections.dat");
                Check(Convert.ToBase64String(original)==Convert.ToBase64String(File.ReadAllBytes(Connection.StoragePathOverride)),"failed write leaves old file intact");
                Check((bool)apply.Invoke(form,new object[]{saved,"127.0.0.2:45831"}),"subsequent successful save clears warning");
                Check(list.DeviceCaption((DiscoveredTv)list.Items[0]).Length==0&&Connection.Load().Endpoint==saved.Endpoint,"new address persists and warning clears");
            }
            Console.WriteLine("PASS: Windows address recovery reports failed writes, preserves draft/session and disk data, and clears warnings only after saving");
        }
        finally
        {
            Connection.StoragePathOverride=previous;
            foreach(string file in Directory.GetFiles(directory))File.Delete(file);
            Directory.Delete(directory);
        }
    }
    private static void Check(bool value,string message){if(!value)throw new Exception(message);}
}
