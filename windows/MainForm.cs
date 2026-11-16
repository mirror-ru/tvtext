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
    public sealed partial class MainForm : Form
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
        private readonly SenderWorkflow workflow=new SenderWorkflow();
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
        private readonly HashSet<string> unsavedConnections=new HashSet<string>(StringComparer.OrdinalIgnoreCase);
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
            BuildMainLayout();
            WireEvents();
            InitializeWindowState();
            HandleCreated+=delegate{Theme.ApplyTitleBar(this);};
            FormClosed+=delegate{StopWork();};ApplyLanguage();ApplyTheme();UpdateControls();
            // Enable DPI autoscaling only after the complete tree is attached.
            // Enabling it before Controls.Add can scale nested table metrics
            // once on parenting and again when this form resumes layout.
            AutoScaleDimensions=new SizeF(96F,96F);AutoScaleMode=AutoScaleMode.Dpi;ResumeLayout(true);
        }
        private void WireEvents()
        {
            WireRefreshAnimation();
            WireButtonEvents();
            WireInputEvents();
            WireDeviceSelectionEvents();
        }
        private void WireRefreshAnimation()
        {
            refreshAnimation.Interval=16;
            refreshAnimation.Tick+=delegate
            {
                double degrees=(refreshClock.ElapsedTicks-refreshAnimationStart)*360.0/Stopwatch.Frequency;
                refreshAngle=(int)(degrees%360.0);refresh.SpinAngle=refreshAngle;refresh.Invalidate();
            };
        }
        private void WireButtonEvents()
        {
            refresh.Click+=async delegate{await ScanAsync(true);};connect.Click+=async delegate{if(IsPairedSelection())await UnpairAsync();else await PairAsync();};send.Click+=async delegate{await SendAsync();};languageButton.Click+=delegate{UiText.Toggle();ApplyLanguage();languageButton.Focus();};themeButton.Click+=delegate{Theme.Toggle();ApplyTheme();themeButton.Focus();};
            rename.Click+=delegate{RenameSelected();};
        }
        private void WireInputEvents()
        {
            input.TextChanged+=delegate{UpdateControls();};
            input.KeyDown+=async delegate(object sender,KeyEventArgs e){if(e.KeyCode==Keys.Enter&&!e.Shift){e.SuppressKeyPress=true;e.Handled=true;await SendAsync();}};
        }
        private void WireDeviceSelectionEvents()
        {
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
        }
        private void InitializeWindowState()
        {
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
    }
}
