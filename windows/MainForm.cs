using System;
using System.Collections.Generic;
using System.Drawing;
using System.Diagnostics;
using System.IO;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Text;
using System.Threading.Tasks;
using CancellationToken=System.Threading.CancellationToken;
using CancellationTokenSource=System.Threading.CancellationTokenSource;
using System.Windows.Forms;

namespace TvText
{
    public sealed class MainForm : Form
    {
        private readonly TvDeviceList devices=new TvDeviceList();
        private readonly RowStyle deviceRow=new RowStyle(SizeType.Absolute,286);
        private readonly ThreeLineTextBox input=new ThreeLineTextBox();
        private readonly NiceButton connect=new NiceButton(),refresh=new NiceButton(),rename=new NiceButton(),send=new NiceButton(),languageButton=new NiceButton(),themeButton=new NiceButton();
        private readonly Label status=new Label();
        private readonly Label versionLabel=new Label(),deviceCaption=new Label(),textPrompt=new Label(),footerHint=new Label();
        private readonly ToolTip tips=new ToolTip();
        private readonly Timer refreshAnimation=new Timer();
        private readonly Stopwatch refreshClock=Stopwatch.StartNew();
        private readonly CancellationTokenSource shutdown=new CancellationTokenSource();
        private CancellationTokenSource selectionWork;
        private Connection connection;
        private readonly List<Connection> profiles;
        private readonly bool preview;
        private Dictionary<string,string> localNames;
        private bool busy,scanning,rebuilding,closing;
        private string suppressedDeviceId=String.Empty;
        private int refreshAngle;
        private long refreshAnimationStart;
        private int selectionRevision;
        private readonly HashSet<string> pairingRequired=new HashSet<string>();
        private readonly Dictionary<string,string> deviceMessages=new Dictionary<string,string>(StringComparer.OrdinalIgnoreCase);
        private List<DiscoveredTv> discovered=new List<DiscoveredTv>();
        public MainForm():this(Connection.Load(),false){}
        internal MainForm(Connection initial,bool preview=false,IEnumerable<Connection> previewProfiles=null,IDictionary<string,string> previewNames=null)
        {
            SuspendLayout();
            selectionWork=CancellationTokenSource.CreateLinkedTokenSource(shutdown.Token);
            this.preview=preview;
            localNames=preview?new Dictionary<string,string>(previewNames??new Dictionary<string,string>(),StringComparer.OrdinalIgnoreCase):DeviceNames.Load();
            profiles=preview?(previewProfiles==null?new List<Connection>():new List<Connection>(previewProfiles)):Connection.LoadAll();
            connection=initial;
            foreach(Connection profile in profiles)if(profile.DeviceId==initial.DeviceId)connection=profile;
            if(preview&&initial.Token.Length>0&&!profiles.Exists(delegate(Connection p){return p.DeviceId==initial.DeviceId;}))profiles.Add(initial);
            Text=UiText.T("app_title");StartPosition=FormStartPosition.CenterScreen;
            try{Icon=Icon.ExtractAssociatedIcon(Application.ExecutablePath);}catch{}
            Font=new Font("Segoe UI",10F);ForeColor=Theme.Ink;
            ClientSize=new Size(680,664);MinimumSize=new Size(610,610);BackColor=Theme.Canvas;
            Panel viewport=new Panel{Name="MainViewport",Dock=DockStyle.Fill,AutoScroll=true,BackColor=Theme.Canvas};
            TableLayoutPanel root=new TableLayoutPanel{Name="MainLayout",Dock=DockStyle.Top,Height=ClientSize.Height,MinimumSize=new Size(0,664),Padding=new Padding(28,23,28,18),ColumnCount=1,RowCount=5};
            viewport.ClientSizeChanged+=delegate{root.Height=Math.Max(root.MinimumSize.Height,viewport.ClientSize.Height);};
            root.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));
            // The native edit control needs a few extra pixels around its
            // three visible lines.  Reclaim that space from the generous
            // header while keeping the device card, send row and footer sizes
            // unchanged.
            root.RowStyles.Add(new RowStyle(SizeType.Absolute,78));root.RowStyles.Add(deviceRow);
            root.RowStyles.Add(new RowStyle(SizeType.Percent,100));root.RowStyles.Add(new RowStyle(SizeType.Absolute,60));root.RowStyles.Add(new RowStyle(SizeType.Absolute,39));
            Panel header=new Panel{Name="Header",Dock=DockStyle.Fill,Margin=Padding.Empty,BackColor=Theme.Canvas};
            header.Controls.Add(new Label{Text=UiText.T("app_title"),Font=new Font("Segoe UI",25F,FontStyle.Bold),Location=new Point(0,1),AutoSize=true});
            versionLabel.ForeColor=Theme.Muted;versionLabel.Location=new Point(2,52);versionLabel.AutoSize=true;header.Controls.Add(versionLabel);
            LinkArt art=new LinkArt{Anchor=AnchorStyles.Right|AnchorStyles.Top};header.Controls.Add(art);
            header.Layout+=delegate
            {
                art.Location=new Point(header.ClientSize.Width-art.Width,0);
            };root.Controls.Add(header,0,0);

            Card deviceCard=new Card{Name="DeviceCard",Dock=DockStyle.Fill,Margin=new Padding(0,0,0,14),Padding=new Padding(17,13,17,10)};
            TableLayoutPanel deviceLayout=new TableLayoutPanel{Name="DeviceLayout",Dock=DockStyle.Fill,BackColor=Theme.Surface,ColumnCount=4,RowCount=3,Margin=Padding.Empty};
            deviceLayout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));
            deviceLayout.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute,146));
            deviceLayout.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute,119));deviceLayout.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute,43));
            deviceLayout.RowStyles.Add(new RowStyle(SizeType.Absolute,36));deviceLayout.RowStyles.Add(new RowStyle(SizeType.Percent,100));deviceLayout.RowStyles.Add(new RowStyle(SizeType.Absolute,22));
            deviceCaption.Font=new Font("Segoe UI",8F,FontStyle.Bold);deviceCaption.ForeColor=Theme.Muted;deviceCaption.Dock=DockStyle.Fill;deviceCaption.TextAlign=ContentAlignment.MiddleLeft;deviceCaption.Margin=Padding.Empty;deviceLayout.Controls.Add(deviceCaption,0,0);
            devices.Name="DeviceList";devices.Dock=DockStyle.Fill;
            devices.Font=new Font("Segoe UI",11F);devices.Margin=new Padding(0,8,0,0);devices.AccessibleName=UiText.T("device_list_accessible");
            devices.IsPairedDevice=delegate(DiscoveredTv tv){return !pairingRequired.Contains(tv.DeviceId)&&profiles.Exists(delegate(Connection p){return p.Token.Length>0&&p.DeviceId==tv.DeviceId;});};
            devices.DeviceCaption=delegate(DiscoveredTv tv)
            {
                string message;return deviceMessages.TryGetValue(tv.DeviceId,out message)?message:String.Empty;
            };
            deviceLayout.Controls.Add(devices,0,1);deviceLayout.SetColumnSpan(devices,4);
            rename.Name="RenameButton";rename.Dock=DockStyle.Fill;rename.Margin=new Padding(0,0,8,0);deviceLayout.Controls.Add(rename,1,0);
            connect.Name="ConnectButton";connect.Primary=true;connect.Dock=DockStyle.Fill;connect.Margin=new Padding(0,0,7,0);deviceLayout.Controls.Add(connect,2,0);
            refresh.Name="RefreshButton";refresh.Glyph="refresh";refresh.Dock=DockStyle.Fill;refresh.Margin=Padding.Empty;refresh.AccessibleName=UiText.T("refresh_accessible");
            tips.SetToolTip(refresh,UiText.T("refresh_tip"));deviceLayout.Controls.Add(refresh,3,0);
            status.Name="ConnectionStatus";status.Dock=DockStyle.Fill;status.ForeColor=Theme.Muted;status.Font=new Font("Segoe UI",9F);status.Margin=new Padding(1,6,0,0);status.AutoEllipsis=true;
            deviceLayout.Controls.Add(status,0,2);deviceLayout.SetColumnSpan(status,4);deviceCard.Controls.Add(deviceLayout);root.Controls.Add(deviceCard,0,1);

            Card textCard=new Card{Name="TextCard",Dock=DockStyle.Top,AutoSize=true,AutoSizeMode=AutoSizeMode.GrowAndShrink,Margin=new Padding(0,0,0,15),Padding=new Padding(20,16,20,14)};
            TableLayoutPanel textLayout=new TableLayoutPanel{Name="TextLayout",Dock=DockStyle.Top,AutoSize=true,AutoSizeMode=AutoSizeMode.GrowAndShrink,BackColor=Theme.Surface,ColumnCount=1,RowCount=2};
            textLayout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));textLayout.RowStyles.Add(new RowStyle(SizeType.Absolute,32));
            textLayout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            textPrompt.Font=new Font("Segoe UI",11F,FontStyle.Bold);textPrompt.AutoSize=true;textPrompt.Margin=Padding.Empty;textLayout.Controls.Add(textPrompt,0,0);
            input.Name="TextBuffer";
            input.Font=new Font("Segoe UI",15F);input.ForeColor=Theme.Ink;input.BackColor=Theme.Surface;input.Dock=DockStyle.Top;input.MaxLength=32768;input.AccessibleName=UiText.T("input_accessible");textLayout.Controls.Add(input,0,1);
            textCard.Controls.Add(textLayout);root.Controls.Add(textCard,0,2);
            send.Name="SendButton";send.Glyph="send";send.Primary=true;send.Font=new Font("Segoe UI",12F,FontStyle.Bold);send.Dock=DockStyle.Fill;send.Margin=new Padding(0,0,0,7);root.Controls.Add(send,0,3);
            TableLayoutPanel footer=new TableLayoutPanel{Name="Footer",Dock=DockStyle.Fill,BackColor=Theme.Canvas,ColumnCount=3,RowCount=1,Margin=Padding.Empty};
            footer.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute,104));footer.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));footer.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute,104));
            footer.RowStyles.Add(new RowStyle(SizeType.Percent,100));
            footerHint.Name="FooterHint";footerHint.Dock=DockStyle.Fill;footerHint.Margin=Padding.Empty;footerHint.TextAlign=ContentAlignment.MiddleCenter;footerHint.ForeColor=Theme.Muted;footerHint.Font=new Font("Segoe UI",9F);footer.Controls.Add(footerHint,1,0);
            TableLayoutPanel footerActions=new TableLayoutPanel{Name="FooterActions",Dock=DockStyle.Fill,ColumnCount=2,RowCount=1,Margin=Padding.Empty,BackColor=Theme.Canvas};
            footerActions.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,50));footerActions.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,50));
            footerActions.RowStyles.Add(new RowStyle(SizeType.Percent,100));
            languageButton.Name="LanguageButton";languageButton.Dock=DockStyle.Fill;languageButton.Margin=new Padding(4,8,4,8);languageButton.Font=new Font("Segoe UI",10F,FontStyle.Bold);footerActions.Controls.Add(languageButton,0,0);
            themeButton.Name="ThemeButton";themeButton.Glyph="theme";themeButton.Dock=DockStyle.Fill;themeButton.Margin=new Padding(0,8,0,8);footerActions.Controls.Add(themeButton,1,0);footer.Controls.Add(footerActions,2,0);
            root.Controls.Add(footer,0,4);
            viewport.Controls.Add(root);Controls.Add(viewport);
            refreshAnimation.Interval=16;
            refreshAnimation.Tick+=delegate
            {
                double degrees=(refreshClock.ElapsedTicks-refreshAnimationStart)*360.0/Stopwatch.Frequency;
                refreshAngle=(int)(degrees%360.0);refresh.SpinAngle=refreshAngle;refresh.Invalidate();
            };
            refresh.Click+=async delegate{await ScanAsync(true);};connect.Click+=async delegate{if(IsPairedSelection())await UnpairAsync();else await PairAsync();};send.Click+=async delegate{await SendAsync();};languageButton.Click+=delegate{UiText.Toggle();ApplyLanguage();languageButton.Focus();};themeButton.Click+=delegate{Theme.Toggle();ApplyTheme();themeButton.Focus();};
            input.TextChanged+=delegate{UpdateControls();};
            rename.Click+=delegate{RenameSelected();};
            input.KeyDown+=async delegate(object sender,KeyEventArgs e){if(e.KeyCode==Keys.Enter&&!e.Shift){e.SuppressKeyPress=true;e.Handled=true;await SendAsync();}};
            devices.SelectedIndexChanged+=delegate
            {
                if(rebuilding)return;
                ResetSelectionWork();
                SyncSelectedConnection(true);
                UpdateControls();
                ClearStatus();
                // Selecting a paired TV is the start of the normal send
                // workflow, so place the caret in the text editor immediately.
                if(ActiveSelection())input.Focus();
            };
            RebuildDevices(null);
            if(Connection.LastReadError.Length>0)ShowMessage(Connection.LastReadError,true);
            else ClearStatus();
            if(!preview)Shown+=async delegate
            {
                Rectangle area=Screen.FromControl(this).WorkingArea;
                if(Height>area.Height-24)Height=Math.Max(MinimumSize.Height,area.Height-24);
                bool hasPairedTv=profiles.Exists(delegate(Connection profile){return profile.Token.Length>0;});
                if(hasPairedTv)
                {
                    if(!closing)input.Focus();
                    return;
                }
                await ScanAsync(false);
                // Keep the convenient initial focus when the user did not
                // select a TV while the initial mDNS search was running.
                if(!closing&&!devices.Focused)input.Focus();
            };
            HandleCreated+=delegate{Theme.ApplyTitleBar(this);};
            FormClosed+=delegate{StopWork();};ApplyLanguage();ApplyTheme();UpdateControls();
            // Enable DPI autoscaling only after the complete tree is attached.
            // Enabling it before Controls.Add can scale nested table metrics
            // once on parenting and again when this form resumes layout.
            AutoScaleDimensions=new SizeF(96F,96F);AutoScaleMode=AutoScaleMode.Dpi;ResumeLayout(true);
        }
        private void ResetSelectionWork()
        {
            selectionWork.Cancel();selectionWork.Dispose();selectionWork=CancellationTokenSource.CreateLinkedTokenSource(shutdown.Token);
            selectionRevision++;
        }
        private void StopWork()
        {
            if(closing)return;closing=true;shutdown.Cancel();selectionWork.Cancel();
            refreshAnimation.Stop();refreshAnimation.Dispose();tips.Dispose();selectionWork.Dispose();shutdown.Dispose();
        }
        protected override void Dispose(bool disposing){if(disposing)StopWork();base.Dispose(disposing);}
        private DiscoveredTv Selected{get{return devices.SelectedItem as DiscoveredTv;}}
        private void ApplyLanguage()
        {
            Text=UiText.T("app_title");
            versionLabel.Text=UiText.T("version",System.Reflection.Assembly.GetExecutingAssembly().GetName().Version.ToString(3));
            deviceCaption.Text=UiText.T("device_caption");
            devices.AccessibleName=UiText.T("device_list_accessible");
            refresh.AccessibleName=UiText.T("refresh_accessible");
            tips.SetToolTip(refresh,UiText.T("refresh_tip"));
            UpdateThemeButton();
            rename.Text=UiText.T("rename");rename.AccessibleName=UiText.T("rename_tv");tips.SetToolTip(rename,UiText.T("rename_tip"));
            textPrompt.Text=UiText.T("text_prompt");
            input.AccessibleName=UiText.T("input_accessible");
            send.Text=UiText.T("send");
            footerHint.Text=UiText.T("footer_hint");
            languageButton.Text=UiText.ButtonText;
            deviceMessages.Clear();devices.EmptyMessage=String.Empty;devices.EmptyMessageError=false;
            devices.Invalidate();
            UpdateControls();
        }
        private void ApplyTheme()
        {
            SuspendLayout();
            ApplyThemeControl(this);
            versionLabel.ForeColor=Theme.Muted;deviceCaption.ForeColor=Theme.Muted;status.ForeColor=Theme.Muted;footerHint.ForeColor=Theme.Muted;
            input.ForeColor=Theme.Ink;input.BackColor=Theme.Surface;
            UpdateThemeButton();Theme.ApplyTitleBar(this);
            ResumeLayout(true);Invalidate(true);
        }
        private void UpdateThemeButton()
        {
            string action=UiText.T(Theme.IsDark?"theme_light":"theme_dark");
            themeButton.AccessibleName=action;themeButton.AccessibleDescription=UiText.T(Theme.IsDark?"theme_current_dark":"theme_current_light");
            tips.SetToolTip(themeButton,action);themeButton.Invalidate();
        }
        private static void ApplyThemeControl(Control control)
        {
            if(control is Form)control.BackColor=Theme.Canvas;
            else if(control is Card)control.BackColor=Color.Transparent;
            else if(control is TvDeviceList)control.BackColor=Theme.Surface;
            else if(control is ThreeLineTextBox)control.BackColor=Theme.Surface;
            else if(control is TextBox)control.BackColor=Theme.Input;
            else if(control.Name=="MainViewport"||control.Name=="MainLayout"||control.Name=="Header"||control.Name=="Footer"||control.Name=="FooterActions")control.BackColor=Theme.Canvas;
            else if(control is Panel||control is TableLayoutPanel||control is FlowLayoutPanel)control.BackColor=Theme.Surface;
            if(control is Label||control is Form||control is TextBox||control is TvDeviceList)control.ForeColor=Theme.Ink;
            if(control is TextBox||control is TvDeviceList)Theme.ApplyNativeControl(control);
            control.Invalidate();
            foreach(Control child in control.Controls)ApplyThemeControl(child);
        }
        private bool ActiveSelection()
        {
            DiscoveredTv selected=Selected;return connection.Token.Length>0&&selected!=null&&(connection.DeviceId.Length>0?selected.DeviceId==connection.DeviceId:selected.Endpoint==connection.Endpoint);
        }
        private bool IsPairedSelection()
        {return ActiveSelection()&&!pairingRequired.Contains(connection.DeviceId);}
        private void SyncSelectedConnection(bool saveSelection)
        {
            DiscoveredTv selected=Selected;
            connection=selected==null?new Connection():profiles.Find(delegate(Connection profile){return profile.DeviceId==selected.DeviceId;})??new Connection();
            if(saveSelection&&!preview&&connection.Token.Length>0){try{connection.Save();}catch{}}
        }
        private void UpdateControls()
        {
            if(IsDisposed||closing)return;
            bool paired=IsPairedSelection();
            send.Enabled=!busy&&paired&&input.Text.Length>0;
            connect.Enabled=!busy&&Selected!=null;connect.Text=paired?UiText.T("unbind"):UiText.T("connect");connect.Danger=false;connect.Primary=!paired;
            refresh.Enabled=!scanning&&!busy;devices.Enabled=!busy;languageButton.Enabled=!busy;themeButton.Enabled=!busy;
            rename.Enabled=!busy&&Selected!=null&&Selected.DeviceId.Length>0;
        }
        private void ShowMessage(string message,bool error)
        {
            if(IsDisposed||closing)return;
            // The common status line stays empty. A message belongs to the
            // selected TV row; with no rows it is rendered inside the list.
            status.Text=String.Empty;status.ForeColor=Theme.Muted;
            DiscoveredTv selected=Selected;
            if(selected!=null)
            {
                deviceMessages[selected.DeviceId]=message;
                devices.EmptyMessage=String.Empty;devices.EmptyMessageError=false;
            }
            else
            {
                deviceMessages.Clear();devices.EmptyMessage=message;devices.EmptyMessageError=error;
            }
            devices.Invalidate();
        }
        private void ClearStatus()
        {
            if(IsDisposed||closing)return;
            status.Text=String.Empty;status.ForeColor=Theme.Muted;
            deviceMessages.Clear();devices.EmptyMessage=String.Empty;devices.EmptyMessageError=false;devices.Invalidate();
        }
        private async Task UnpairAsync()
        {
            if(busy||closing||!IsPairedSelection())return;
            if(MessageBox.Show(this,UiText.T("confirm_unbind"),UiText.T("unbind_title"),MessageBoxButtons.YesNo,MessageBoxIcon.Question,MessageBoxDefaultButton.Button1)!=DialogResult.Yes)return;
            Connection saved=connection;string removedId=saved.DeviceId;bool remoteNotified=false;bool refreshAfterUnpair=false;
            busy=true;UpdateControls();ShowMessage(UiText.T("unpairing"),false);
            try
            {
                try
                {
                    Reply reply=await Task.Run(delegate{return Client.Exchange(saved.Endpoint,saved.Fingerprint,4,saved.Token,"","",shutdown.Token);});
                    if(closing)return;
                    Client.VerifyIdentity(reply,saved);
                    // AUTH means the token was already removed on the TV, which is a successful end state.
                    remoteNotified=reply.Code==0||reply.Code==1;
                }
                catch(Exception){if(closing)return;}
                try{RemoveProfile(removedId);}
                catch(Exception e){MessageBox.Show(this,e.Message,UiText.T("unbind_failed"),MessageBoxButtons.OK,MessageBoxIcon.Error);return;}
                refreshAfterUnpair=true;
                if(!remoteNotified)
                    MessageBox.Show(this,UiText.T("unbind_remote_failed"),UiText.T("unbind_remote_failed_title"),MessageBoxButtons.OK,MessageBoxIcon.Warning);
            }
            finally
            {
                busy=false;UpdateControls();
            }
            if(refreshAfterUnpair&&!closing)await ScanAsync(false);
        }
        private void RemoveProfile(string deviceId)
        {
            if(!preview)Connection.Forget(deviceId);
            profiles.RemoveAll(delegate(Connection profile){return profile.DeviceId==deviceId;});
            pairingRequired.Remove(deviceId);suppressedDeviceId=deviceId;
            RebuildDevices(null);ClearStatus();
        }
        private void RenameSelected()
        {
            DiscoveredTv tv=Selected;
            if(busy||closing||tv==null)return;
            using(RenameDialog dialog=new RenameDialog(tv.ToString()))
            {
                if(dialog.ShowDialog(this)!=DialogResult.OK)return;
                try{RenameDevice(tv.DeviceId,dialog.DeviceName);ClearStatus();}
                catch(Exception error){MessageBox.Show(this,error.Message,UiText.T("rename_failed"),MessageBoxButtons.OK,MessageBoxIcon.Error);}
            }
        }
        private void RenameDevice(string deviceId,string name)
        {
            Guid id;
            if(!Guid.TryParse(deviceId,out id)||!DeviceNames.ValidName(name))throw new ArgumentException(UiText.T("name_required"));
            if(preview)localNames[id.ToString()]=name.Trim();
            else localNames=DeviceNames.Set(id.ToString(),name);
            RebuildDevices(null);
        }
        private DiscoveredTv PresentDevice(DiscoveredTv tv)
        {
            string alias;localNames.TryGetValue(tv.DeviceId,out alias);
            return new DiscoveredTv{Name=tv.Name,DeviceId=tv.DeviceId,Endpoint=tv.Endpoint,LocalName=alias};
        }
        private void ShowProfiles()
        {
            using(Form dialog=new Form())
            {
                dialog.Text=UiText.T("connected_tvs");dialog.StartPosition=FormStartPosition.CenterParent;dialog.FormBorderStyle=FormBorderStyle.FixedDialog;
                dialog.MinimizeBox=false;dialog.MaximizeBox=false;dialog.ShowInTaskbar=false;dialog.ClientSize=new Size(470,360);dialog.BackColor=Theme.Canvas;
                TableLayoutPanel root=new TableLayoutPanel{Dock=DockStyle.Fill,Padding=new Padding(18),ColumnCount=1,RowCount=3,BackColor=Theme.Canvas};
                root.RowStyles.Add(new RowStyle(SizeType.Absolute,34));root.RowStyles.Add(new RowStyle(SizeType.Percent,100));root.RowStyles.Add(new RowStyle(SizeType.Absolute,42));
                root.Controls.Add(new Label{Text=UiText.T("saved_connections"),Dock=DockStyle.Fill,Font=new Font("Segoe UI",12F,FontStyle.Bold),ForeColor=Theme.Ink,TextAlign=ContentAlignment.MiddleLeft},0,0);
                FlowLayoutPanel list=new FlowLayoutPanel{Dock=DockStyle.Fill,FlowDirection=FlowDirection.TopDown,WrapContents=false,AutoScroll=true,BackColor=Theme.Surface,Padding=new Padding(10),Margin=Padding.Empty};
                root.Controls.Add(list,0,1);
                NiceButton close=new NiceButton{Text=UiText.T("close"),Dock=DockStyle.Fill,Margin=new Padding(0,7,0,0)};close.Click+=delegate{dialog.Close();};root.Controls.Add(close,0,2);
                Action rebuild=null;rebuild=delegate
                {
                    list.SuspendLayout();list.Controls.Clear();
                    if(profiles.Count==0)
                    {
                        Panel empty=new Panel{Name="EmptyState",Width=Math.Max(100,list.ClientSize.Width-list.Padding.Horizontal),Height=Math.Max(100,list.ClientSize.Height-list.Padding.Vertical),BackColor=Theme.Surface,Margin=Padding.Empty};
                        empty.Controls.Add(new Label{Text=UiText.T("no_saved_tvs"),Dock=DockStyle.Fill,ForeColor=Theme.Muted,TextAlign=ContentAlignment.MiddleCenter,Padding=new Padding(8)});
                        list.Controls.Add(empty);
                    }
                    else foreach(Connection profile in profiles.ToArray())
                    {
                        Panel item=new Panel{Width=Math.Max(100,list.ClientSize.Width-20),Height=56,BackColor=Theme.Row,Margin=new Padding(0,0,0,8)};
                        item.Paint+=delegate(object sender,PaintEventArgs e)
                        {
                            using(Pen border=new Pen(Theme.Border))e.Graphics.DrawRectangle(border,0,0,item.Width-1,item.Height-1);
                        };
                        Label name=new Label{Text=profile.Name,AutoEllipsis=true,Font=new Font("Segoe UI",10F,FontStyle.Bold),ForeColor=Theme.Ink,Location=new Point(12,7),Size=new Size(Math.Max(100,item.Width-72),23),Anchor=AnchorStyles.Left|AnchorStyles.Top|AnchorStyles.Right};
                        Label id=new Label{Text=profile.DeviceId.Length>=6?profile.DeviceId.Substring(0,6):profile.Endpoint,AutoEllipsis=true,Font=new Font("Segoe UI",8F),ForeColor=Theme.Muted,Location=new Point(12,31),Size=new Size(Math.Max(100,item.Width-72),18),Anchor=AnchorStyles.Left|AnchorStyles.Top|AnchorStyles.Right};
                        NiceButton forget=new NiceButton{Glyph="delete",Danger=true,Size=new Size(40,40),Location=new Point(Math.Max(0,item.Width-48),8),Anchor=AnchorStyles.Top|AnchorStyles.Right,AccessibleName=UiText.T("delete")};
                        forget.Click+=delegate
                        {
                            if(MessageBox.Show(dialog,UiText.T("confirm_delete",profile.Name),UiText.T("delete_title"),MessageBoxButtons.YesNo,MessageBoxIcon.Question,MessageBoxDefaultButton.Button2)!=DialogResult.Yes)return;
                            try{RemoveProfile(profile.DeviceId);}
                            catch(Exception e){MessageBox.Show(dialog,e.Message,UiText.T("delete_failed"),MessageBoxButtons.OK,MessageBoxIcon.Error);return;}
                            UpdateControls();rebuild();
                        };
                        item.Controls.Add(forget);item.Controls.Add(name);item.Controls.Add(id);list.Controls.Add(item);
                    }
                    list.ResumeLayout(true);
                };
                list.Resize+=delegate
                {
                    foreach(Control item in list.Controls)if(item is Panel)
                    {
                        item.Width=Math.Max(100,list.ClientSize.Width-list.Padding.Horizontal-SystemInformation.VerticalScrollBarWidth-2);
                        if(item.Name=="EmptyState")item.Height=Math.Max(100,list.ClientSize.Height-list.Padding.Vertical);
                        foreach(Control child in item.Controls)if(child is NiceButton)child.Location=new Point(Math.Max(0,item.Width-child.Width-8),8);
                    }
                };
                rebuild();dialog.Controls.Add(root);dialog.AcceptButton=close;dialog.CancelButton=close;dialog.ShowDialog(this);
            }
        }
        private void RebuildDevices(string preferredId)
        {
            DiscoveredTv previous=Selected;
            string desiredId=!String.IsNullOrEmpty(preferredId)?preferredId:previous!=null?previous.DeviceId:connection.DeviceId;
            List<object> next=new List<object>();
            HashSet<string> listed=new HashSet<string>();
            foreach(Connection profile in profiles){next.Add(PresentDevice(new DiscoveredTv{Name=profile.Name,Endpoint=profile.Endpoint,DeviceId=profile.DeviceId}));listed.Add(profile.DeviceId);}
            foreach(DiscoveredTv tv in discovered)if(tv.DeviceId!=suppressedDeviceId&&listed.Add(tv.DeviceId))next.Add(PresentDevice(tv));
            // A disappearing advertisement must never silently switch the recipient.
            if(previous!=null&&previous.DeviceId!=suppressedDeviceId&&listed.Add(previous.DeviceId))next.Add(PresentDevice(previous));
            int selection=-1;
            for(int i=0;i<next.Count;i++)
            {
                DiscoveredTv item=(DiscoveredTv)next[i];
                if(item.DeviceId==desiredId){selection=i;break;}
            }
            bool changed=next.Count!=devices.Items.Count;
            for(int i=0;!changed&&i<next.Count;i++)
            {
                DiscoveredTv a=next[i] as DiscoveredTv,b=devices.Items[i] as DiscoveredTv;
                changed=a!=null&&b!=null?a.DeviceId!=b.DeviceId||a.Name!=b.Name||a.LocalName!=b.LocalName||a.Endpoint!=b.Endpoint:!Object.Equals(next[i],devices.Items[i]);
            }
            rebuilding=true;
            int target=selection>=0?selection:next.Count>0?0:-1;
            try{if(changed)devices.ReplaceItems(next,target);else devices.SelectedIndex=target;}
            finally{rebuilding=false;}
            if((previous==null?String.Empty:previous.DeviceId)!=(Selected==null?String.Empty:Selected.DeviceId))ResetSelectionWork();
            SyncSelectedConnection(false);
            UpdateControls();
        }
        private async Task ScanAsync(bool userInitiated)
        {
            if(scanning||busy||closing)return;scanning=true;refreshAnimationStart=refreshClock.ElapsedTicks;refreshAngle=0;refresh.SpinAngle=0;refresh.Spinning=true;refreshAnimation.Start();ClearStatus();UpdateControls();
            int revision=selectionRevision;
            try
            {
                List<DiscoveredTv> list=await Discovery.FindAsync(5000,shutdown.Token);if(closing)return;discovered=list;suppressedDeviceId=String.Empty;
                if(busy)return;
                Connection saved=connection;CancellationToken token=selectionWork.Token;int peerRevision=selectionRevision;
                DiscoveredTv relocated=saved.Token.Length>0&&ActiveSelection()?await ConnectionResolver.FindVerifiedAsync(saved,list,token):null;
                if(closing)return;
                if(relocated!=null&&!busy&&peerRevision==selectionRevision&&ActiveSelection()&&Object.ReferenceEquals(saved,connection))
                {saved.Endpoint=relocated.Endpoint;try{saved.Save();}catch{}ClearStatus();}
                RebuildDevices(null);
                if(Connection.LastReadError.Length==0&&!busy&&revision==selectionRevision)
                {
                    ClearStatus();
                }
            }
            catch(OperationCanceledException){}
            catch(Exception e){if(!busy&&revision==selectionRevision&&(userInitiated||connection.Token.Length==0))ShowMessage(UiText.T("discovery_failed_detail",e.GetBaseException().Message),true);}
            finally{scanning=false;refreshAnimation.Stop();refresh.Spinning=false;refresh.SpinAngle=0;refresh.Invalidate();UpdateControls();}
        }
        private async Task PairAsync()
        {
            if(busy||closing)return;DiscoveredTv tv=Selected;if(tv==null){await ScanAsync(true);return;}
            busy=true;ResetSelectionWork();UpdateControls();
            try
            {
                string host=tv.Endpoint,pairingCode;
                using(PairDialog dialog=new PairDialog())
                {if(dialog.ShowDialog(this)!=DialogResult.OK)return;pairingCode=dialog.Code;}
                foreach(DiscoveredTv found in discovered)if(found.DeviceId==tv.DeviceId){host=found.Endpoint;break;}
                CancellationToken token=shutdown.Token;
                ShowMessage(UiText.T("pairing"),false);string fingerprint=await Task.Run(delegate{return Client.Probe(host,token);});if(closing)return;
                if(fingerprint!=connection.Fingerprint)
                {
                    DialogResult answer=MessageBox.Show(this,UiText.T("fingerprint_question",Client.ShortFingerprint(fingerprint)),UiText.T("fingerprint_title"),MessageBoxButtons.YesNo,MessageBoxIcon.Question,MessageBoxDefaultButton.Button1);
                    if(answer!=DialogResult.Yes){ClearStatus();return;}
                }
                Reply reply=await Task.Run(delegate{return Client.Exchange(host,fingerprint,1,pairingCode,"","",token);});if(closing)return;
                if(reply.Code!=0||reply.Token.Length!=64){ShowMessage(UiText.ResponseMessage(reply.Code==0?6:reply.Code),true);return;}
                Guid deviceId;if(!Guid.TryParse(reply.DeviceId,out deviceId)){ShowMessage(UiText.T("update_apk"),true);return;}
                if(tv.DeviceId.Length>0&&tv.DeviceId!=deviceId.ToString()){ShowMessage(UiText.T("changed_id"),true);return;}
                string previousName=null;foreach(Connection profile in profiles)if(profile.DeviceId==deviceId.ToString())previousName=profile.Name;
                connection=new Connection{Endpoint=host,Fingerprint=fingerprint,Token=reply.Token,DeviceId=deviceId.ToString(),Name=previousName??tv.Name};
                if(suppressedDeviceId==connection.DeviceId)suppressedDeviceId=String.Empty;
                pairingRequired.Remove(connection.DeviceId);
                int profileIndex=profiles.FindIndex(delegate(Connection profile){return profile.DeviceId==connection.DeviceId;});
                if(profileIndex>=0)profiles[profileIndex]=connection;else profiles.Add(connection);
                bool saved=true;try{connection.Save();}catch{saved=false;}
                RebuildDevices(connection.DeviceId);if(!saved)ShowMessage(UiText.T("success_save_failed"),true);else ClearStatus();input.Focus();
            }
            catch(OperationCanceledException){}
            catch(Exception e){ShowMessage(ErrorText(e,false),true);}
            finally{busy=false;UpdateControls();}
        }
        private async Task SendAsync()
        {
            if(busy||closing||!ActiveSelection()||pairingRequired.Contains(connection.DeviceId)||input.Text.Length==0)return;string text=input.Text;
            if(Encoding.UTF8.GetByteCount(text)>Client.MaxText){ShowMessage(UiText.T("too_long"),true);return;}
            Connection saved=connection;string id=Guid.NewGuid().ToString();busy=true;CancellationToken token=shutdown.Token;Exception sendError=null;UpdateControls();
            try
            {
                Reply reply=await Task.Run(delegate{return Client.Exchange(saved.Endpoint,saved.Fingerprint,3,saved.Token,id,text,token);});if(closing)return;
                Client.VerifyIdentity(reply,saved);
                if(reply.Code==1&&pairingRequired.Add(saved.DeviceId))devices.Invalidate();
                if(reply.Code==0)ClearStatus();else ShowMessage(UiText.ResponseMessage(reply.Code),true);
                if(reply.Code==0&&input.Text==text)input.Clear();input.Focus();
            }
            catch(OperationCanceledException){}
            catch(Exception e)
            {
                sendError=e;
            }
            if(sendError!=null&&!closing)
            {
                if(sendError is AuthenticationException&&pairingRequired.Add(saved.DeviceId))devices.Invalidate();
                bool refreshed=false;
                if(IsNetworkError(sendError)&&!(sendError is AuthenticationException))refreshed=await RefreshEndpointAsync(saved,token);
                ShowMessage(refreshed?UiText.T("endpoint_updated"):ErrorText(sendError,true),true);
            }
            busy=false;UpdateControls();
        }
        private async Task<bool> RefreshEndpointAsync(Connection saved,CancellationToken token)
        {
            if(closing||saved==null||saved.DeviceId.Length==0)return false;
            int revision=selectionRevision;
            try
            {
                List<DiscoveredTv> list=await Discovery.FindAsync(2500,token);
                if(closing||revision!=selectionRevision||!Object.ReferenceEquals(saved,connection))return false;
                discovered=list;suppressedDeviceId=String.Empty;
                DiscoveredTv relocated=await ConnectionResolver.FindVerifiedAsync(saved,list,token);
                if(closing||revision!=selectionRevision||!Object.ReferenceEquals(saved,connection))return false;
                RebuildDevices(saved.DeviceId);
                if(relocated==null)return false;
                saved.Endpoint=relocated.Endpoint;
                try{saved.Save();}catch{}
                RebuildDevices(saved.DeviceId);
                return true;
            }
            catch(OperationCanceledException){return false;}
            catch{return false;}
        }
        private static bool IsNetworkError(Exception error)
        {return error is IOException||error is SocketException||error is TimeoutException;}
        private static string ErrorText(Exception error,bool sending)
        {
            if(error is AuthenticationException)return UiText.T("auth_error");
            if(error is EncoderFallbackException)return UiText.T("bad_character");
            if(sending&&(error is IOException||error is TimeoutException||error is SocketException))return UiText.T("send_no_confirmation");
            if(error is SocketException||error is IOException||error is TimeoutException)return UiText.T("tv_unavailable");
            return error.Message;
        }
    }
    internal sealed class PairDialog : Form
    {
        private readonly TextBox code=new TextBox();
        internal string Code{get{return code.Text.Trim();}}
        internal PairDialog()
        {
            SuspendLayout();AutoScaleDimensions=new SizeF(96F,96F);
            Text=UiText.T("pair_dialog_title");ClientSize=new Size(460,320);FormBorderStyle=FormBorderStyle.FixedDialog;
            MaximizeBox=false;MinimizeBox=false;ShowInTaskbar=false;StartPosition=FormStartPosition.CenterParent;BackColor=Theme.Canvas;AutoScaleMode=AutoScaleMode.Dpi;Font=new Font("Segoe UI",10F);ForeColor=Theme.Ink;
            Panel shell=new Panel{Dock=DockStyle.Fill,BackColor=Theme.Canvas,Padding=new Padding(12)};
            Card card=new Card{Dock=DockStyle.Fill,Margin=Padding.Empty,Padding=new Padding(24)};shell.Controls.Add(card);
            TableLayoutPanel root=new TableLayoutPanel{Dock=DockStyle.Fill,BackColor=Theme.Surface,ColumnCount=1,RowCount=6,Margin=Padding.Empty};root.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));
            root.RowStyles.Add(new RowStyle(SizeType.Absolute,44));root.RowStyles.Add(new RowStyle(SizeType.Absolute,56));
            root.RowStyles.Add(new RowStyle(SizeType.Absolute,28));root.RowStyles.Add(new RowStyle(SizeType.Absolute,56));
            root.RowStyles.Add(new RowStyle(SizeType.Percent,100));root.RowStyles.Add(new RowStyle(SizeType.Absolute,44));
            TableLayoutPanel heading=new TableLayoutPanel{Dock=DockStyle.Fill,BackColor=Theme.Surface,ColumnCount=2,RowCount=1,Margin=Padding.Empty};
            heading.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute,40));heading.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));
            heading.RowStyles.Add(new RowStyle(SizeType.Percent,100));
            TvBadge badge=new TvBadge{Anchor=AnchorStyles.Left,Size=new Size(40,40),Margin=Padding.Empty};heading.Controls.Add(badge,0,0);
            heading.Controls.Add(new Label{Name="PairHeading",Text=UiText.T("pair_dialog_heading"),Dock=DockStyle.Fill,Font=new Font("Segoe UI",18F,FontStyle.Bold),ForeColor=Theme.Ink,TextAlign=ContentAlignment.MiddleLeft,Margin=new Padding(12,0,0,0)},1,0);
            root.Controls.Add(heading,0,0);
            root.Controls.Add(new Label{Name="PairInstructions",Text=UiText.T("pair_dialog_hint"),Dock=DockStyle.Fill,ForeColor=Theme.Muted,TextAlign=ContentAlignment.MiddleLeft,Margin=Padding.Empty},0,1);
            root.Controls.Add(new Label{Text=UiText.T("pair_code"),Dock=DockStyle.Fill,ForeColor=Theme.Muted,Font=new Font("Segoe UI",9F),TextAlign=ContentAlignment.TopLeft,Margin=Padding.Empty},0,2);
            code.Name="PairCode";code.Font=new Font("Segoe UI",20F);code.TextAlign=HorizontalAlignment.Center;code.MaxLength=8;code.BorderStyle=BorderStyle.None;code.BackColor=Theme.Input;code.ForeColor=Theme.Ink;code.AccessibleName=UiText.T("pair_code_accessible");code.TabIndex=0;code.ImeMode=ImeMode.Disable;
            code.KeyPress+=delegate(object sender,KeyPressEventArgs e){if(!Char.IsControl(e.KeyChar)&&!Char.IsDigit(e.KeyChar))e.Handled=true;};
            code.TextChanged+=delegate
            {
                string raw=code.Text;string clean=System.Text.RegularExpressions.Regex.Replace(raw,"[^0-9]",String.Empty);
                if(raw==clean)return;int caret=Math.Min(code.SelectionStart,clean.Length);code.Text=clean;code.SelectionStart=caret;
            };
            PairCodeField codeField=new PairCodeField(code){Name="PairCodeField",Dock=DockStyle.Fill,Margin=Padding.Empty};root.Controls.Add(codeField,0,3);
            NiceButton ok=new NiceButton{Name="PairConnectButton",Text=UiText.T("connect"),Primary=true,Dock=DockStyle.Fill,Margin=Padding.Empty,TabIndex=1};root.Controls.Add(ok,0,5);
            ok.Click+=delegate
            {
                if(!System.Text.RegularExpressions.Regex.IsMatch(Code,"^[0-9]{8}$")){code.Focus();return;}DialogResult=DialogResult.OK;
            };
            AcceptButton=ok;card.Controls.Add(root);Controls.Add(shell);HandleCreated+=delegate{Theme.ApplyTitleBar(this);};Shown+=delegate{code.Focus();};ResumeLayout(true);
        }
    }

}
