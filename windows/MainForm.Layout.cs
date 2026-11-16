using System;
using System.Drawing;
using System.Windows.Forms;

namespace TvText
{
    public sealed partial class MainForm
    {
        private void BuildMainLayout()
        {
            Text = UiText.T("app_title"); StartPosition = FormStartPosition.CenterScreen;
            try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }
            Font = new Font("Segoe UI", 10F); ForeColor = Theme.Ink;
            ClientSize = new Size(680, 664); MinimumSize = new Size(610, 610); BackColor = Theme.Canvas;
            Panel viewport = new Panel { Name = "MainViewport", Dock = DockStyle.Fill, AutoScroll = true, BackColor = Theme.Canvas };
            TableLayoutPanel root = new TableLayoutPanel { Name = "MainLayout", Dock = DockStyle.Top, Height = ClientSize.Height, MinimumSize = new Size(0, 664), Padding = new Padding(28, 23, 28, 18), ColumnCount = 1, RowCount = 5 };
            viewport.ClientSizeChanged += delegate { root.Height = Math.Max(root.MinimumSize.Height, viewport.ClientSize.Height); };
            root.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
            BuildHeader(root); BuildDeviceCard(root); BuildTextCard(root); BuildFooter(root);
            viewport.Controls.Add(root); Controls.Add(viewport);
        }
        private void BuildHeader(TableLayoutPanel root)
        {
            // The native edit control needs a few extra pixels around its
            // three visible lines. Reclaim that space from the generous
            // header while keeping device card and footer sizes unchanged.
            root.RowStyles.Add(new RowStyle(SizeType.Absolute, 78)); root.RowStyles.Add(deviceRow);
            root.RowStyles.Add(new RowStyle(SizeType.Percent, 100)); root.RowStyles.Add(new RowStyle(SizeType.Absolute, 60)); root.RowStyles.Add(new RowStyle(SizeType.Absolute, 39));
            Panel header = new Panel { Name = "Header", Dock = DockStyle.Fill, Margin = Padding.Empty, BackColor = Theme.Canvas };
            header.Controls.Add(new Label { Text = UiText.T("app_title"), Font = new Font("Segoe UI", 25F, FontStyle.Bold), Location = new Point(0, 1), AutoSize = true });
            versionLabel.ForeColor = Theme.Muted; versionLabel.Location = new Point(2, 52); versionLabel.AutoSize = true; header.Controls.Add(versionLabel);
            LinkArt art = new LinkArt { Anchor = AnchorStyles.Right | AnchorStyles.Top }; header.Controls.Add(art);
            header.Layout += delegate { art.Location = new Point(header.ClientSize.Width - art.Width, 0); }; root.Controls.Add(header, 0, 0);
        }
        private void BuildDeviceCard(TableLayoutPanel root)
        {
            Card deviceCard = new Card { Name = "DeviceCard", Dock = DockStyle.Fill, Margin = new Padding(0, 0, 0, 14), Padding = new Padding(17, 13, 17, 10) };
            TableLayoutPanel deviceLayout = new TableLayoutPanel { Name = "DeviceLayout", Dock = DockStyle.Fill, BackColor = Theme.Surface, ColumnCount = 4, RowCount = 3, Margin = Padding.Empty };
            deviceLayout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100)); deviceLayout.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 146));
            deviceLayout.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 119)); deviceLayout.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 43));
            deviceLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 36)); deviceLayout.RowStyles.Add(new RowStyle(SizeType.Percent, 100)); deviceLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 22));
            deviceCaption.Font = new Font("Segoe UI", 8F, FontStyle.Bold); deviceCaption.ForeColor = Theme.Muted; deviceCaption.Dock = DockStyle.Fill; deviceCaption.TextAlign = ContentAlignment.MiddleLeft; deviceCaption.Margin = Padding.Empty; deviceLayout.Controls.Add(deviceCaption, 0, 0);
            devices.Name = "DeviceList"; devices.Dock = DockStyle.Fill; devices.Font = new Font("Segoe UI", 11F); devices.Margin = new Padding(0, 8, 0, 0); devices.AccessibleName = UiText.T("device_list_accessible");
            devices.IsPairedDevice = delegate (DiscoveredTv tv) { return !pairingRequired.Contains(tv.DeviceId) && profiles.Exists(delegate (Connection p) { return p.Token.Length > 0 && p.DeviceId == tv.DeviceId; }); };
            devices.DeviceCaption = delegate (DiscoveredTv tv) { string message; return deviceMessages.TryGetValue(tv.DeviceId, out message) ? message : unsavedConnections.Contains(tv.DeviceId) ? UiText.T("save_failed") : String.Empty; };
            deviceLayout.Controls.Add(devices, 0, 1); deviceLayout.SetColumnSpan(devices, 4);
            rename.Name = "RenameButton"; rename.Dock = DockStyle.Fill; rename.Margin = new Padding(0, 0, 8, 0); deviceLayout.Controls.Add(rename, 1, 0);
            connect.Name = "ConnectButton"; connect.Primary = true; connect.Dock = DockStyle.Fill; connect.Margin = new Padding(0, 0, 7, 0); deviceLayout.Controls.Add(connect, 2, 0);
            refresh.Name = "RefreshButton"; refresh.Glyph = "refresh"; refresh.Dock = DockStyle.Fill; refresh.Margin = Padding.Empty; refresh.AccessibleName = UiText.T("refresh_accessible"); tips.SetToolTip(refresh, UiText.T("refresh_tip")); deviceLayout.Controls.Add(refresh, 3, 0);
            status.Name = "ConnectionStatus"; status.Dock = DockStyle.Fill; status.ForeColor = Theme.Muted; status.Font = new Font("Segoe UI", 9F); status.Margin = new Padding(1, 6, 0, 0); status.AutoEllipsis = true;
            deviceLayout.Controls.Add(status, 0, 2); deviceLayout.SetColumnSpan(status, 4); deviceCard.Controls.Add(deviceLayout); root.Controls.Add(deviceCard, 0, 1);
        }
        private void BuildTextCard(TableLayoutPanel root)
        {
            Card textCard = new Card { Name = "TextCard", Dock = DockStyle.Top, AutoSize = true, AutoSizeMode = AutoSizeMode.GrowAndShrink, Margin = new Padding(0, 0, 0, 15), Padding = new Padding(20, 16, 20, 14) };
            TableLayoutPanel textLayout = new TableLayoutPanel { Name = "TextLayout", Dock = DockStyle.Top, AutoSize = true, AutoSizeMode = AutoSizeMode.GrowAndShrink, BackColor = Theme.Surface, ColumnCount = 1, RowCount = 2 };
            textLayout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100)); textLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 32)); textLayout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            textPrompt.Font = new Font("Segoe UI", 11F, FontStyle.Bold); textPrompt.AutoSize = true; textPrompt.Margin = Padding.Empty; textLayout.Controls.Add(textPrompt, 0, 0);
            input.Name = "TextBuffer"; input.Font = new Font("Segoe UI", 15F); input.ForeColor = Theme.Ink; input.BackColor = Theme.Surface; input.Dock = DockStyle.Top; input.MaxLength = 32768; input.AccessibleName = UiText.T("input_accessible"); textLayout.Controls.Add(input, 0, 1);
            textCard.Controls.Add(textLayout); root.Controls.Add(textCard, 0, 2);
            send.Name = "SendButton"; send.Glyph = "send"; send.Primary = true; send.Font = new Font("Segoe UI", 12F, FontStyle.Bold); send.Dock = DockStyle.Fill; send.Margin = new Padding(0, 0, 0, 7); root.Controls.Add(send, 0, 3);
        }
        private void BuildFooter(TableLayoutPanel root)
        {
            TableLayoutPanel footer = new TableLayoutPanel { Name = "Footer", Dock = DockStyle.Fill, BackColor = Theme.Canvas, ColumnCount = 3, RowCount = 1, Margin = Padding.Empty };
            footer.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 104)); footer.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100)); footer.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 104)); footer.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            footerHint.Name = "FooterHint"; footerHint.Dock = DockStyle.Fill; footerHint.Margin = Padding.Empty; footerHint.TextAlign = ContentAlignment.MiddleCenter; footerHint.ForeColor = Theme.Muted; footerHint.Font = new Font("Segoe UI", 9F); footer.Controls.Add(footerHint, 1, 0);
            TableLayoutPanel footerActions = new TableLayoutPanel { Name = "FooterActions", Dock = DockStyle.Fill, ColumnCount = 2, RowCount = 1, Margin = Padding.Empty, BackColor = Theme.Canvas };
            footerActions.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 50)); footerActions.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 50)); footerActions.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            languageButton.Name = "LanguageButton"; languageButton.Dock = DockStyle.Fill; languageButton.Margin = new Padding(4, 8, 4, 8); languageButton.Font = new Font("Segoe UI", 10F, FontStyle.Bold); footerActions.Controls.Add(languageButton, 0, 0);
            themeButton.Name = "ThemeButton"; themeButton.Glyph = "theme"; themeButton.Dock = DockStyle.Fill; themeButton.Margin = new Padding(0, 8, 0, 8); footerActions.Controls.Add(themeButton, 1, 0); footer.Controls.Add(footerActions, 2, 0);
            root.Controls.Add(footer, 0, 4);
        }
    }
}
