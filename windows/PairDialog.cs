using System;
using System.Drawing;
using System.Windows.Forms;

namespace TvText
{
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
