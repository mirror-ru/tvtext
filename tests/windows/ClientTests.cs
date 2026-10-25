using System;
using System.IO;
using System.Text;
using System.Drawing;
using System.Windows.Forms;
using System.Security.Authentication;
using System.Reflection;
using System.Threading.Tasks;
using TvText;

public static class ClientTests
{
    private static void Check(bool condition, string message) { if (!condition) throw new Exception(message); }
    [STAThread] public static int Main(string[] args)
    {
        Application.SetUnhandledExceptionMode(UnhandledExceptionMode.ThrowException);
        try { return Run(args); }
        catch(Exception error) { Console.Error.WriteLine(error); return 1; }
    }
    private static int Run(string[] args)
    {
        if(args[0]=="profile-writer")return ClientReliabilityTests.ProfileWriter(args[1]);
        if(args[0]=="pair-dialog")return PairDialogTests.Run(args[1]);
        if(args[0]=="device-list")return DeviceListTests.Run(args[1]);
        if(args[0]=="theme")return ThemeTests.Run(args[1]);
        if(args[0]=="dpi-layout")return DpiLayoutTests.Run();
        if(args[0]=="connection-save"){ConnectionSaveTests.Run();return 0;}
        if(args[0]=="sender-workflow"){SenderWorkflowTests.Run();return 0;}
        if (args[0] == "preview")
        {
            Application.EnableVisualStyles();
            Connection demo = new Connection { DeviceId = Guid.NewGuid().ToString(), Name = "Гостиная · Google TV", Token = "preview-only" };
            Connection secondDemo = new Connection { DeviceId = Guid.NewGuid().ToString(), Name = "Спальня · Google TV", Token = "preview-only-2" };
            using (MainForm form = new MainForm(demo, true, new[] { demo, secondDemo }))
            {
                form.StartPosition = FormStartPosition.Manual; form.Location = new Point(-20000, -20000); form.ShowInTaskbar = false;
                form.Show(); Application.DoEvents(); form.PerformLayout();
                Control picker=form.Controls.Find("DeviceList",true)[0];
                Check(picker.Height>=((TvDeviceList)picker).ItemHeight*2,"both TV rows are immediately visible");
                form.Controls.Find("TextBuffer",true)[0].Text = "Дюна: Часть вторая";
                Check(form.Controls.Find("ConnectionStatus",true)[0].Text.Length==0,"ready selection does not add a persistent status line");
                using (Bitmap bitmap = new Bitmap(form.Width, form.Height)) { form.DrawToBitmap(bitmap, new Rectangle(0, 0, form.Width, form.Height)); bitmap.Save(args[1]); }
                using (PairDialog dialog = new PairDialog())
                {
                    dialog.StartPosition=FormStartPosition.Manual;dialog.Location=new Point(-20000,-20000);dialog.ShowInTaskbar=false;dialog.Show();Application.DoEvents();
                    using(Bitmap bitmap=new Bitmap(dialog.Width,dialog.Height)){dialog.DrawToBitmap(bitmap,new Rectangle(0,0,dialog.Width,dialog.Height));bitmap.Save(args[1]+".pair.png");}dialog.Close();
                }
                form.Close();
            }
            Console.WriteLine("PASS: Windows Forms preview rendered"); return 0;
        }
        try
        {
            ConnectionCatalogTests.Run();
            string endpoint = "127.0.0.1:45832";
            ClientReliabilityTests.Run(Path.GetDirectoryName(Path.GetFullPath(args[0])));
            string[] details = File.ReadAllLines(args[0]);
            string pin = details[0], fingerprint = details[1];
            Check(Client.Probe(endpoint) == fingerprint, "certificate probe");
            bool rejected = false;
            try { Client.Exchange(endpoint, new string('0', 64), 1, pin, "", ""); } catch (AuthenticationException) { rejected = true; }
            Check(rejected, "wrong certificate must be rejected before sending PIN");
            Check(Client.Exchange(endpoint, fingerprint, 2, "bad", "", "").Code == 1, "unauthorized status");
            Reply pair = Client.Exchange(endpoint, fingerprint, 1, pin, "", "");
            Check(pair.Code == 0 && pair.Token.Length == 64, "pairing");
            Check(Client.Exchange(endpoint, fingerprint, 2, pair.Token, "", "").Ready, "status");
            Check(pair.DeviceId == DiscoveryTests.FixtureId, "stable TV identity returned over pinned TLS");
            DiscoveryTests.Profiles(Path.GetDirectoryName(Path.GetFullPath(args[0])));
            DiscoveryTests.Reconnect(endpoint, fingerprint, pair.Token);
            DiscoveryTests.NativeBrowse();
            string text = "Привет, ТВ! café 中文 😀 & % + \" \\ \nвторая строка";
            string id = Guid.NewGuid().ToString();
            Check(Client.Exchange(endpoint, fingerprint, 3, pair.Token, id, text).Code == 0, "unicode insert");
            Check(Client.Exchange(endpoint, fingerprint, 3, pair.Token, id, text).Code == 0, "deduplication");
            Check(Client.Exchange(endpoint, fingerprint, 3, pair.Token, Guid.NewGuid().ToString(), "C# → Kotlin: Привет 😀 & % + \" \\ \n第二行").Code == 0, "second insert");
            bool oversized = false;
            try { Client.Exchange(endpoint, fingerprint, 3, pair.Token, Guid.NewGuid().ToString(), new string('Ж', 20000)); }
            catch (ArgumentException) { oversized = true; }
            Check(oversized, "UTF-8 size enforced");
            Reply other = Client.Exchange("127.0.0.1:45833", fingerprint, 1, details[2], "", "");
            Check(other.Code == 0 && other.Token != pair.Token,"independent pairing for second TV");
            Connection firstConnection=new Connection { Endpoint=endpoint,DeviceId=pair.DeviceId,Fingerprint=fingerprint,Token=pair.Token,Name="Гостиная" };
            Connection secondConnection=new Connection { Endpoint="127.0.0.1:45833",DeviceId=other.DeviceId,Fingerprint=fingerprint,Token=other.Token,Name="Спальня" };
            RemovedSelectionChecks(firstConnection,secondConnection);
            UiConnectionChecks(firstConnection,secondConnection);
            UiSwitch(firstConnection,secondConnection);
            Console.WriteLine("PASS: C# client → Kotlin TLS server: certificate pinning, pairing, auth, status, Unicode, duplicates, UTF-8 limit");
            return 0;
        }
        finally { File.WriteAllText(args[1], "done"); }
    }
    private static void RemovedSelectionChecks(Connection first,Connection second)
    {
        using(MainForm form=new MainForm(first,true,new[]{first,second}))
        {
            form.StartPosition=FormStartPosition.Manual;form.Location=new Point(-20000,-20000);form.ShowInTaskbar=false;
            form.Show();Application.DoEvents();
            MethodInfo remove=typeof(MainForm).GetMethod("RemoveProfile",BindingFlags.Instance|BindingFlags.NonPublic);
            remove.Invoke(form,new object[]{first.DeviceId});
            form.Controls.Find("TextBuffer",true)[0].Text="Draft for remaining TV";
            ListBox list=(ListBox)form.Controls.Find("DeviceList",true)[0];
            Check(list.Items.Count==1&&((DiscoveredTv)list.SelectedItem).DeviceId==second.DeviceId,"removal selects the remaining saved TV");
            Check(form.Controls.Find("SendButton",true)[0].Enabled&&form.Controls.Find("ConnectButton",true)[0].Text==UiText.T("unbind"),"remaining pairing is active without another selection");
            remove.Invoke(form,new object[]{second.DeviceId});
            Check(list.Items.Count==0&&!form.Controls.Find("SendButton",true)[0].Enabled,"removing the final TV clears the active connection");
            form.Close();
        }
        MethodInfo setter=typeof(UiText).GetProperty("Current",BindingFlags.Static|BindingFlags.NonPublic).GetSetMethod(true);
        string language=UiText.Current;
        try
        {
            setter.Invoke(null,new object[]{"en"});
            foreach(int code in new[]{1,2,3,4,5,6,9})
            {
                string message=UiText.ResponseMessage(code);
                Check(message.Length>0,"every known error has a local message");
                foreach(char c in message)Check(c<'\u0400'||c>'\u04ff',"EN replies do not contain Russian receiver messages");
            }
            setter.Invoke(null,new object[]{"ru"});Check(UiText.ResponseMessage(4).Contains("клавиатуру"),"RU reply stays localized");
            Check(UiText.ResponseMessage(0)==String.Empty,"success remains silent");
        }
        finally{setter.Invoke(null,new object[]{language});}
        Console.WriteLine("PASS: unpair synchronizes the remaining TV and active credentials; RU/EN response codes are localized");
    }
    private static void UiConnectionChecks(Connection first,Connection second)
    {
        System.Net.Sockets.TcpListener stalled=new System.Net.Sockets.TcpListener(System.Net.IPAddress.Loopback,0);stalled.Start();
        Exception failure=null;
        Connection offline=new Connection{DeviceId=Guid.NewGuid().ToString(),Name="Ожидание",Endpoint=Client.FormatEndpoint(System.Net.IPAddress.Loopback,((System.Net.IPEndPoint)stalled.LocalEndpoint).Port),Token=first.Token,Fingerprint=first.Fingerprint};
        using(Form host=new Form{StartPosition=FormStartPosition.Manual,Location=new Point(-20000,-20000),ShowInTaskbar=false})
        using(MainForm form=new MainForm(offline,true,new[]{offline,second}))
        {
            form.StartPosition=FormStartPosition.Manual;form.Location=new Point(-20000,-20000);form.ShowInTaskbar=false;
            host.Shown+=async delegate
            {
                try
                {
                    form.Show(host);
                    Task<System.Net.Sockets.TcpClient> accepted=stalled.AcceptTcpClientAsync();
                    await Task.Delay(500);
                    Check(!accepted.IsCompleted,"opening the window must not start a background status connection");
                    ((TvDeviceList)form.Controls.Find("DeviceList",true)[0]).SelectedIndex=1;
                    await Task.Delay(250);
                    Check(!accepted.IsCompleted,"switching TVs must not poll the selected TV");
                    Check(form.Controls.Find("ConnectionStatus",true)[0].Text.Length==0,"selecting a TV does not add a persistent ready message");
                    Check(form.Controls.Find("ConnectionStatus",true)[0].Text.IndexOf("Подключено",StringComparison.Ordinal)<0,"UI must not claim a live connection");
                    MethodInfo scan=typeof(MainForm).GetMethod("ScanAsync",BindingFlags.Instance|BindingFlags.NonPublic);
                    Task scanning=(Task)scan.Invoke(form,new object[]{true});await Task.Delay(100);
                    NiceButton refresh=(NiceButton)form.Controls.Find("RefreshButton",true)[0];
                    Check(refresh.Spinning,"refresh button must spin while discovery is running");
                    int angle=refresh.SpinAngle;await Task.Delay(120);Check(refresh.SpinAngle!=angle,"refresh animation must advance while discovery is running");
                    System.Threading.CancellationToken token=((System.Threading.CancellationTokenSource)typeof(MainForm).GetField("shutdown",BindingFlags.Instance|BindingFlags.NonPublic).GetValue(form)).Token;
                    form.Close();Check(token.IsCancellationRequested,"window close must cancel background work");
                    Check(await Task.WhenAny(scanning,Task.Delay(2000))==scanning,"window close must finish discovery promptly");await scanning;
                    Check(!refresh.Spinning,"refresh button must stop spinning after discovery ends");
                }
                catch(Exception error){failure=error;}
                finally{form.Close();host.Close();}
            };
            Application.Run(host);
        }
        stalled.Stop();if(failure!=null)throw failure;
        Console.WriteLine("PASS: UI selection does not poll in the background, neutral status copy, window close cancels discovery");
    }
    private static void UiSwitch(Connection first,Connection second)
    {
        Application.EnableVisualStyles();
        Exception failure=null;
        using(MainForm form=new MainForm(first,true,new[]{first,second}))
        {
            form.StartPosition=FormStartPosition.Manual;form.Location=new Point(-20000,-20000);form.ShowInTaskbar=false;
            form.Shown+=async delegate
            {
                try
                {
                    TvDeviceList picker=(TvDeviceList)form.Controls.Find("DeviceList",true)[0];TextBox input=(TextBox)form.Controls.Find("TextBuffer",true)[0];
                    MethodInfo key=typeof(Control).GetMethod("OnKeyDown",BindingFlags.Instance|BindingFlags.NonPublic);
                    DeviceListChecks(form,picker,key);
                    picker.SelectedIndex=1;input.Text="UI → спальня";
                    KeyEventArgs shift=new KeyEventArgs(Keys.Shift|Keys.Enter);key.Invoke(input,new object[]{shift});Check(!shift.SuppressKeyPress,"Shift+Enter must remain a newline");
                    KeyEventArgs enter=new KeyEventArgs(Keys.Enter);key.Invoke(input,new object[]{enter});Check(enter.SuppressKeyPress,"Enter must send without inserting a newline");await WaitForSendAsync(form);
                    Check(input.Text.Length==0,"successful Enter send must clear the input by default");
                    picker.SelectedIndex=0;input.Text="UI → гостиная";((Button)form.Controls.Find("SendButton",true)[0]).PerformClick();await WaitForSendAsync(form);
                    Check(input.Text.Length==0,"successful button send must clear the input by default");
                    input.Text="UI → черновик";((Button)form.Controls.Find("SendButton",true)[0]).PerformClick();input.Text="Следующий текст";await WaitForSendAsync(form);
                    Check(input.Text=="Следующий текст","a draft edited while sending must survive acknowledgement of earlier text");
                    second.Token="revoked-test-token";picker.SelectedIndex=1;input.Text="Сохранить при ошибке";
                    Check(form.Controls.Find("SendButton",true)[0].Enabled,"send must be available before a key is rejected");
                    ((Button)form.Controls.Find("SendButton",true)[0]).PerformClick();await WaitForSendAsync(form);
                    Check(input.Text=="Сохранить при ошибке","rejected send must preserve input");
                    Check(form.Controls.Find("ConnectButton",true)[0].Enabled,"re-pairing must be available on the main button without a menu; status="+form.Controls.Find("ConnectionStatus",true)[0].Text);
                    Check(!form.Controls.Find("SendButton",true)[0].Enabled,"revoked key must disable sending until re-paired");
                }
                catch(Exception error){failure=error;}
                finally{form.Close();}
            };
            Application.Run(form);
        }
        if(failure!=null)throw failure;
        Console.WriteLine("PASS: TV selection, Enter/Shift+Enter, Send, default clear on success, preservation on error, menu-free re-pairing");
    }
    private static void DeviceListChecks(MainForm form,TvDeviceList list,MethodInfo key)
    {
        Control input=form.Controls.Find("TextBuffer",true)[0];
        Check(list.Visible&&list.Items.Count==2&&list.ClientSize.Height>=list.ItemHeight*2,"both TV rows are visible without opening a selector");
        Check(list.AccessibleRole==AccessibleRole.List,"device list exposes native list accessibility");
        key.Invoke(list,new object[]{new KeyEventArgs(Keys.Down)});Check(list.SelectedIndex==1,"Down selects second TV");
        key.Invoke(list,new object[]{new KeyEventArgs(Keys.Home)});Check(list.SelectedIndex==0,"Home selects first TV");
        Rectangle second=list.GetItemRectangle(1);
        typeof(Control).GetMethod("OnMouseMove",BindingFlags.Instance|BindingFlags.NonPublic).Invoke(list,new object[]{new MouseEventArgs(MouseButtons.None,0,12,second.Top+second.Height/2,0)});
        Check(list.Cursor==Cursors.Hand,"TV rows use a pointer cursor");
        typeof(Control).GetMethod("OnMouseDown",BindingFlags.Instance|BindingFlags.NonPublic).Invoke(list,new object[]{new MouseEventArgs(MouseButtons.Left,1,12,second.Top+second.Height/2,0)});
        Check(list.SelectedIndex==1&&!list.Focused&&input.Focused,"clicking a paired TV selects it and focuses the text editor");
        object selected=list.SelectedItem;int redraws=0,changes=0;
        InvalidateEventHandler invalidated=delegate{redraws++;};EventHandler changed=delegate{changes++;};
        list.Invalidated+=invalidated;list.SelectedIndexChanged+=changed;
        MethodInfo rebuild=typeof(MainForm).GetMethod("RebuildDevices",BindingFlags.Instance|BindingFlags.NonPublic);
        for(int i=0;i<30;i++)rebuild.Invoke(form,new object[]{null});
        list.Invalidated-=invalidated;list.SelectedIndexChanged-=changed;
        Check(Object.ReferenceEquals(selected,list.SelectedItem),"unchanged discovery preserves the selected row");
        Check(redraws==0&&changes==0,"unchanged discovery must not redraw the list or emit selection changes");
        // Two advertised addresses for one ID must produce one row; losing a TV must retain the recipient.
        FieldInfo discovered=typeof(MainForm).GetField("discovered",BindingFlags.Instance|BindingFlags.NonPublic);
        string unknown=Guid.NewGuid().ToString();
        discovered.SetValue(form,new System.Collections.Generic.List<DiscoveredTv>{new DiscoveredTv{DeviceId=unknown,Name="Кабинет",Endpoint=((DiscoveredTv)list.Items[0]).Endpoint},new DiscoveredTv{DeviceId=unknown,Name="Кабинет",Endpoint="[::1]:45832"}});
        rebuild.Invoke(form,new object[]{null});Check(list.Items.Count==3,"duplicate advertisements for a single TV must merge");
        list.SelectedIndex=2;
        Check(!form.Controls.Find("SendButton",true)[0].Enabled&&form.Controls.Find("ConnectButton",true)[0].Enabled,"a different TV ID sharing the old endpoint must remain unpaired");
        discovered.SetValue(form,new System.Collections.Generic.List<DiscoveredTv>());rebuild.Invoke(form,new object[]{null});
        Check(((DiscoveredTv)list.SelectedItem).DeviceId==unknown,"disappearing TV must not switch the recipient");
        typeof(MainForm).GetField("suppressedDeviceId",BindingFlags.Instance|BindingFlags.NonPublic).SetValue(form,unknown);
        rebuild.Invoke(form,new object[]{null});
        bool stale=false;for(int i=0;i<list.Items.Count;i++)if(((DiscoveredTv)list.Items[i]).DeviceId==unknown)stale=true;
        Check(!stale,"an unpaired device removed from the saved list must not return after refresh");
        list.SelectedIndex=0;
        Console.WriteLine("PASS: open TV list, direct mouse selection, keyboard navigation, native accessibility, unchanged discovery and recipient preservation");
    }
    private static async Task WaitForSendAsync(MainForm form)
    {
        FieldInfo busy=typeof(MainForm).GetField("busy",BindingFlags.Instance|BindingFlags.NonPublic);
        DateTime until=DateTime.UtcNow.AddSeconds(15);
        while((bool)busy.GetValue(form)&&DateTime.UtcNow<until)await Task.Delay(10);
        Check(!(bool)busy.GetValue(form),"UI send timeout");
    }
}
