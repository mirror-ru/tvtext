using System;
using System.Drawing;
using System.Windows.Forms;

namespace TvText
{
    internal sealed class RenameDialog : Form
    {
        private readonly TextBox name=new TextBox();
        internal string DeviceName{get{return name.Text.Trim();}}
        internal RenameDialog(string currentName)
        {
            SuspendLayout();
            Text=UiText.T("rename_tv");StartPosition=FormStartPosition.CenterParent;FormBorderStyle=FormBorderStyle.FixedDialog;
            MinimizeBox=false;MaximizeBox=false;ShowInTaskbar=false;ClientSize=new Size(480,282);
            Font=new Font("Segoe UI",10F);ForeColor=Theme.Ink;BackColor=Theme.Canvas;
            Panel shell=new Panel{Dock=DockStyle.Fill,Padding=new Padding(12),BackColor=Theme.Canvas};
            Card card=new Card{Name="RenameCard",Dock=DockStyle.Fill,Padding=new Padding(24),Margin=Padding.Empty};shell.Controls.Add(card);
            TableLayoutPanel root=new TableLayoutPanel{Dock=DockStyle.Fill,BackColor=Theme.Surface,ColumnCount=1,RowCount=5,Margin=Padding.Empty};
            root.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));
            root.RowStyles.Add(new RowStyle(SizeType.Absolute,44));root.RowStyles.Add(new RowStyle(SizeType.Absolute,36));
            root.RowStyles.Add(new RowStyle(SizeType.Absolute,52));root.RowStyles.Add(new RowStyle(SizeType.Percent,100));root.RowStyles.Add(new RowStyle(SizeType.Absolute,40));
            TableLayoutPanel heading=new TableLayoutPanel{Dock=DockStyle.Fill,BackColor=Theme.Surface,ColumnCount=2,RowCount=1,Margin=Padding.Empty};
            heading.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute,40));heading.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));
            heading.Controls.Add(new TvBadge{Size=new Size(36,36),Anchor=AnchorStyles.Left,Margin=Padding.Empty},0,0);
            heading.Controls.Add(new Label{Name="RenameHeading",Text=UiText.T("rename_heading"),Font=new Font("Segoe UI",17F,FontStyle.Bold),ForeColor=Theme.Ink,Dock=DockStyle.Fill,TextAlign=ContentAlignment.MiddleLeft,Margin=new Padding(10,0,0,0)},1,0);
            root.Controls.Add(heading,0,0);
            root.Controls.Add(new Label{Name="RenameHint",Text=UiText.T("rename_hint"),ForeColor=Theme.Muted,Dock=DockStyle.Fill,TextAlign=ContentAlignment.MiddleLeft,Margin=Padding.Empty},0,1);
            name.Name="DeviceNameInput";name.Font=new Font("Segoe UI",13F);name.BorderStyle=BorderStyle.None;name.BackColor=Theme.Input;name.ForeColor=Theme.Ink;
            name.MaxLength=DeviceNames.MaxNameLength;name.Text=currentName;name.AccessibleName=UiText.T("rename_heading");name.TabIndex=0;
            root.Controls.Add(new PairCodeField(name){Name="DeviceNameField",Dock=DockStyle.Fill,Margin=Padding.Empty},0,2);
            TableLayoutPanel actions=new TableLayoutPanel{Dock=DockStyle.Fill,BackColor=Theme.Surface,ColumnCount=3,RowCount=1,Margin=Padding.Empty};
            actions.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));actions.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute,110));actions.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute,132));
            NiceButton save=new NiceButton{Name="SaveNameButton",Text=UiText.T("save"),Primary=true,Dock=DockStyle.Fill,Margin=Padding.Empty,TabIndex=1};
            NiceButton cancel=new NiceButton{Name="CancelNameButton",Text=UiText.T("cancel"),Dock=DockStyle.Fill,Margin=new Padding(0,0,10,0),DialogResult=DialogResult.Cancel,TabIndex=2};
            save.Click+=delegate{if(DeviceNames.ValidName(name.Text))DialogResult=DialogResult.OK;};
            name.TextChanged+=delegate{save.Enabled=DeviceNames.ValidName(name.Text);};save.Enabled=DeviceNames.ValidName(name.Text);
            actions.Controls.Add(cancel,1,0);actions.Controls.Add(save,2,0);root.Controls.Add(actions,0,4);
            AcceptButton=save;CancelButton=cancel;card.Controls.Add(root);Controls.Add(shell);
            HandleCreated+=delegate{Theme.ApplyTitleBar(this);};Shown+=delegate{name.Focus();name.SelectAll();};
            AutoScaleDimensions=new SizeF(96F,96F);AutoScaleMode=AutoScaleMode.Dpi;ResumeLayout(true);
        }
    }
}
