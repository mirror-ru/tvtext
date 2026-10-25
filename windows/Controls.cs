using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace TvText
{
    internal sealed class Card : Panel
    {
        internal Card(){DoubleBuffered=true;BackColor=Color.Transparent;Padding=new Padding(18);}
        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode=SmoothingMode.AntiAlias;
            using(GraphicsPath path=Theme.Round(new RectangleF(.5f,.5f,Width-1,Height-1),18))
            using(Brush fill=new SolidBrush(Theme.Surface))
            using(Pen border=new Pen(Theme.Border)){e.Graphics.FillPath(fill,path);e.Graphics.DrawPath(border,path);}
        }
    }
    internal sealed class PairCodeField : Panel
    {
        private readonly TextBox editor;
        internal PairCodeField(TextBox editor)
        {
            this.editor=editor;
            SetStyle(ControlStyles.OptimizedDoubleBuffer|ControlStyles.AllPaintingInWmPaint|ControlStyles.ResizeRedraw,true);
            BackColor=Theme.Surface;Cursor=Cursors.IBeam;TabStop=false;
            Controls.Add(editor);
            editor.Enter+=delegate{Invalidate();};editor.Leave+=delegate{Invalidate();};
            editor.FontChanged+=delegate{PerformLayout();};
        }
        protected override void OnLayout(LayoutEventArgs e)
        {
            base.OnLayout(e);
            if(editor==null)return;
            int inset=(int)Math.Round(14*DeviceDpi/96f);
            int height=editor.PreferredHeight;
            // A single-line native TextBox keeps its own height; center the
            // whole control so its text and caret share the same baseline.
            editor.SetBounds(inset,Math.Max(0,(ClientSize.Height-height)/2),Math.Max(1,ClientSize.Width-2*inset),height);
        }
        protected override void OnMouseDown(MouseEventArgs e){base.OnMouseDown(e);if(e.Button==MouseButtons.Left)editor.Focus();}
        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);
            float scale=DeviceDpi/96f;
            e.Graphics.SmoothingMode=SmoothingMode.AntiAlias;
            using(GraphicsPath path=Theme.Round(new RectangleF(.75f,.75f,Width-1.5f,Height-1.5f),10*scale))
            using(Brush fill=new SolidBrush(Theme.Input))
            using(Pen border=new Pen(editor.Focused?Theme.Blue:Theme.Border,(editor.Focused?1.3f:1f)*scale))
            {e.Graphics.FillPath(fill,path);e.Graphics.DrawPath(border,path);}
        }
    }
    internal sealed class ThreeLineTextBox : TextBox
    {
        private bool scrollbarVisible,updatingScrollbar;
        internal ThreeLineTextBox()
        {Multiline=true;AcceptsReturn=true;WordWrap=true;ScrollBars=ScrollBars.Vertical;BorderStyle=BorderStyle.None;AutoSize=false;}
        // A native multiline edit control keeps a small top/bottom inset inside
        // its client area.  Font.Height*3 therefore clips the third baseline
        // on some Windows fonts and DPI settings.  Keep the logical three-line
        // layout, but reserve enough inset for all three lines to be visible at
        // once.
        internal int ThreeLineHeight{get{return Font.Height*3+Math.Max(6,(int)Math.Round(Font.Height*.3f));}}
        public override Size GetPreferredSize(Size proposedSize){return new Size(base.GetPreferredSize(proposedSize).Width,ThreeLineHeight);}
        protected override void SetBoundsCore(int x,int y,int width,int height,BoundsSpecified specified)
        {base.SetBoundsCore(x,y,width,ThreeLineHeight,specified);}
        protected override void OnFontChanged(EventArgs e){base.OnFontChanged(e);Height=ThreeLineHeight;UpdateScrollbar();}
        protected override void OnDpiChangedAfterParent(EventArgs e){base.OnDpiChangedAfterParent(e);Height=ThreeLineHeight;UpdateScrollbar();}
        protected override void OnHandleCreated(EventArgs e){base.OnHandleCreated(e);Theme.ApplyNativeControl(this);scrollbarVisible=true;UpdateScrollbar();}
        protected override void OnTextChanged(EventArgs e){base.OnTextChanged(e);UpdateScrollbar();}
        protected override void OnResize(EventArgs e){base.OnResize(e);UpdateScrollbar();}
        private void UpdateScrollbar()
        {
            if(!IsHandleCreated||updatingScrollbar)return;
            updatingScrollbar=true;
            try
            {
                bool needed=SendMessage(Handle,0x00BA,IntPtr.Zero,IntPtr.Zero).ToInt32()>3;
                if(needed!=scrollbarVisible){scrollbarVisible=needed;ShowScrollBar(Handle,1,needed);}
            }
            finally{updatingScrollbar=false;}
        }
        [DllImport("user32.dll",CharSet=CharSet.Auto)]private static extern IntPtr SendMessage(IntPtr window,int message,IntPtr wParam,IntPtr lParam);
        [DllImport("user32.dll")]private static extern bool ShowScrollBar(IntPtr window,int bar,bool show);
    }
    internal sealed class NiceButton : Button
    {
        internal bool Primary=false;
        internal bool Danger=false;
        internal string Glyph="";
        internal bool Spinning=false;
        internal int SpinAngle;
        private bool hover;
        internal NiceButton()
        {
            SetStyle(ControlStyles.UserPaint|ControlStyles.AllPaintingInWmPaint|ControlStyles.OptimizedDoubleBuffer,true);
            FlatStyle=FlatStyle.Flat;FlatAppearance.BorderSize=0;Cursor=Cursors.Hand;Font=new Font("Segoe UI",10F,FontStyle.Bold);
        }
        protected override void OnMouseEnter(EventArgs e){hover=true;Invalidate();base.OnMouseEnter(e);}
        protected override void OnMouseLeave(EventArgs e){hover=false;Invalidate();base.OnMouseLeave(e);}
        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g=e.Graphics;g.SmoothingMode=SmoothingMode.AntiAlias;g.Clear(Parent==null?Theme.Canvas:Parent.BackColor);
            Color fill=!Enabled?Theme.DisabledButton:Danger?(hover?Color.FromArgb(173,47,66):Color.FromArgb(200,64,82)):Primary?(hover?Theme.PrimaryHover:Theme.PrimaryFill):hover?Theme.ButtonHover:Theme.Button;
            Color ink=!Enabled?Theme.Muted:(Danger||Primary)?Color.White:Theme.ButtonInk;
            using(GraphicsPath path=Theme.Round(new RectangleF(0,0,Width-1,Height-1),11))
            using(Brush brush=new SolidBrush(fill))g.FillPath(brush,path);
            if(Glyph=="refresh")
            {
                GraphicsState state=g.Save();
                try
                {
                    using(Pen pen=new Pen(ink,1.8f))
                    {
                        g.TranslateTransform(Width/2f,Height/2f);
                        g.RotateTransform(Spinning?SpinAngle:0);
                        g.DrawArc(pen,-8.5f,-8.5f,17,17,40,285);
                        g.DrawLine(pen,8.5f,-8.5f,8.5f,-2.5f);
                        g.DrawLine(pen,8.5f,-2.5f,2.5f,-3.5f);
                    }
                }
                finally{g.Restore(state);}
            }
            else if(Glyph=="theme")
            {
                GraphicsState themeState=g.Save();
                float scale=DeviceDpi/96f;
                g.TranslateTransform(Width/2f,Height/2f);g.ScaleTransform(scale,scale);
                float cx=0,cy=0;
                using(Pen pen=new Pen(ink,1.7f))
                using(Brush brush=new SolidBrush(ink))
                {
                    if(Theme.IsDark)
                    {
                        g.FillEllipse(brush,cx-4,cy-4,8,8);
                        for(int i=0;i<8;i++)
                        {
                            double angle=i*Math.PI/4;
                            float x1=cx+(float)Math.Cos(angle)*7,y1=cy+(float)Math.Sin(angle)*7;
                            float x2=cx+(float)Math.Cos(angle)*9,y2=cy+(float)Math.Sin(angle)*9;
                            g.DrawLine(pen,x1,y1,x2,y2);
                        }
                    }
                    else
                    {
                        g.FillEllipse(brush,cx-7,cy-8,15,15);
                        using(Brush cutout=new SolidBrush(fill))g.FillEllipse(cutout,cx-2,cy-10,15,15);
                    }
                }
                g.Restore(themeState);
            }
            else if(Glyph=="delete")
            {
                float x=Width/2f,y=(Height-20)/2f;
                using(Pen pen=new Pen(ink,1.8f)){pen.StartCap=LineCap.Round;pen.EndCap=LineCap.Round;
                    g.DrawRectangle(pen,x-7,y+4,14,14);g.DrawLine(pen,x-9,y+1,x+9,y+1);g.DrawLine(pen,x-4,y-2,x+4,y-2);
                    g.DrawLine(pen,x-3,y+8,x-3,y+14);g.DrawLine(pen,x,y+8,x,y+14);g.DrawLine(pen,x+3,y+8,x+3,y+14);
                }
            }
            else
            {
                TextRenderer.DrawText(g,Text,Font,ClientRectangle,ink,TextFormatFlags.HorizontalCenter|TextFormatFlags.VerticalCenter);
                if(Glyph=="send")
                {
                    float x=Width-36,y=Height/2f;
                    using(Pen pen=new Pen(ink,1.8f)){g.DrawLine(pen,x-9,y,x+7,y);g.DrawLine(pen,x+1,y-6,x+7,y);g.DrawLine(pen,x+1,y+6,x+7,y);}
                }
            }
            if(Focused&&ShowFocusCues)using(Pen pen=new Pen(Danger?Color.FromArgb(145,35,52):Theme.Blue,1))
            using(GraphicsPath path=Theme.Round(new RectangleF(3,3,Width-7,Height-7),8))g.DrawPath(pen,path);
        }
    }
    internal sealed class TvDeviceList : ListBox
    {
        internal const int VisibleRows=3;
        private int hoverIndex=-1;
        private Font captionFont;
        private Font CaptionFont{get{return captionFont??(captionFont=new Font(Font.FontFamily,Math.Max(8F,Font.Size*.82F),FontStyle.Regular));}}
        internal Func<DiscoveredTv,bool> IsPairedDevice;
        internal Func<DiscoveredTv,string> DeviceCaption;
        internal string EmptyMessage=String.Empty;
        internal bool EmptyMessageError;
        internal TvDeviceList()
        {
            BorderStyle=BorderStyle.None;IntegralHeight=false;DrawMode=DrawMode.OwnerDrawFixed;
            BackColor=Theme.Surface;ForeColor=Theme.Ink;SelectionMode=SelectionMode.One;ScrollAlwaysVisible=false;
            ItemHeight=60;AccessibleRole=AccessibleRole.List;
        }
        protected override void OnHandleCreated(EventArgs e){base.OnHandleCreated(e);Theme.ApplyNativeControl(this);}
        public override Size GetPreferredSize(Size proposedSize){return new Size(base.GetPreferredSize(proposedSize).Width,ItemHeight*VisibleRows);}
        protected override void SetBoundsCore(int x,int y,int width,int height,BoundsSpecified specified)
        {base.SetBoundsCore(x,y,width,ItemHeight*VisibleRows,specified);}
        internal void ReplaceItems(IEnumerable<object> replacement,int selection)
        {
            int top=Items.Count>0?TopIndex:0;
            BeginUpdate();
            try
            {
                Items.Clear();
                foreach(object item in replacement)Items.Add(item);
                SelectedIndex=selection;
                if(Items.Count>0)
                {
                    int page=Math.Max(1,ClientSize.Height/ItemHeight);
                    int nextTop=Math.Min(top,Math.Max(0,Items.Count-page));
                    if(selection<nextTop)nextTop=Math.Max(0,selection);
                    else if(selection>=nextTop+page)nextTop=selection-page+1;
                    TopIndex=nextTop;
                }
                hoverIndex=-1;
            }
            finally{EndUpdate();}
            Invalidate();
        }
        protected override void OnFontChanged(EventArgs e)
        {
            base.OnFontChanged(e);
            if(captionFont!=null){captionFont.Dispose();captionFont=null;}
            ItemHeight=Math.Max(Font.Height*2+16,(int)Math.Round(60*DeviceDpi/96f));
            Height=ItemHeight*VisibleRows;
        }
        protected override void OnDpiChangedAfterParent(EventArgs e)
        {
            base.OnDpiChangedAfterParent(e);
            ItemHeight=Math.Max(Font.Height*2+16,(int)Math.Round(60*DeviceDpi/96f));
            Height=ItemHeight*VisibleRows;
        }
        protected override void OnDrawItem(DrawItemEventArgs e)
        {
            if(e.Index<0||e.Index>=Items.Count)return;
            Graphics g=e.Graphics;g.SmoothingMode=SmoothingMode.AntiAlias;
            using(Brush background=new SolidBrush(BackColor))g.FillRectangle(background,e.Bounds);
            DiscoveredTv tv=Items[e.Index] as DiscoveredTv;
            if(tv==null)return;
            float scale=DeviceDpi/96f;
            bool selected=e.Index==SelectedIndex,hot=Enabled&&e.Index==hoverIndex;
            RectangleF bounds=new RectangleF(e.Bounds.X+1,e.Bounds.Y+2*scale,e.Bounds.Width-3,e.Bounds.Height-6*scale);
            Color fill=selected?Theme.RowSelected:hot?Theme.RowHover:Theme.Row;
            using(GraphicsPath path=Theme.Round(bounds,11*scale))
            using(Brush brush=new SolidBrush(fill))
            {
                g.FillPath(brush,path);
                if(selected)
                using(Pen border=new Pen(Enabled?Theme.SelectedBorder:Theme.Border,scale))g.DrawPath(border,path);
            }
            float iconSize=34*scale,iconX=bounds.X+10*scale,iconY=bounds.Y+(bounds.Height-iconSize)/2;
            using(GraphicsPath path=Theme.Round(new RectangleF(iconX,iconY,iconSize,iconSize),10*scale))
            using(Brush brush=new SolidBrush(selected?Theme.Surface:Theme.IconSurface))g.FillPath(brush,path);
            Theme.Tv(g,new RectangleF(iconX+8*scale,iconY+9*scale,18*scale,19*scale),Enabled?Theme.Blue:Theme.Muted,1.5f*scale);
            int left=(int)(iconX+iconSize+12*scale),right=(int)(bounds.Right-40*scale);
            int titleHeight=Font.Height+2,captionHeight=CaptionFont.Height+2;
            int textY=(int)(bounds.Y+(bounds.Height-titleHeight-captionHeight-2*scale)/2);
            TextRenderer.DrawText(g,tv.ToString(),Font,new Rectangle(left,textY,Math.Max(1,right-left),titleHeight),Enabled?Theme.Ink:Theme.Muted,
                TextFormatFlags.EndEllipsis|TextFormatFlags.NoPrefix|TextFormatFlags.SingleLine|TextFormatFlags.NoPadding);
            bool paired=IsPairedDevice!=null&&IsPairedDevice(tv);
            string caption=DeviceCaption!=null?DeviceCaption(tv):String.Empty;
            bool customCaption=!String.IsNullOrEmpty(caption);
            if(!customCaption)caption=paired?UiText.T("paired"):UiText.T("device_needs_pairing");
            Color captionColor=Theme.Muted;
            TextRenderer.DrawText(g,caption,CaptionFont,new Rectangle(left,textY+titleHeight+(int)(2*scale),Math.Max(1,right-left),captionHeight),captionColor,
                TextFormatFlags.EndEllipsis|TextFormatFlags.NoPrefix|TextFormatFlags.SingleLine|TextFormatFlags.NoPadding);
            if(selected)
            {
                float cx=bounds.Right-20*scale,cy=bounds.Y+bounds.Height/2;
                using(Brush brush=new SolidBrush(Enabled?Theme.PrimaryFill:Theme.Muted))g.FillEllipse(brush,cx-9*scale,cy-9*scale,18*scale,18*scale);
                using(Pen check=new Pen(Color.White,1.7f*scale))
                {check.StartCap=LineCap.Round;check.EndCap=LineCap.Round;g.DrawLines(check,new[]{new PointF(cx-4*scale,cy),new PointF(cx-1*scale,cy+3*scale),new PointF(cx+4*scale,cy-3*scale)});}
                if(Focused&&ShowFocusCues)
                using(Pen focus=new Pen(Theme.Blue,scale))
                using(GraphicsPath path=Theme.Round(new RectangleF(bounds.X+3*scale,bounds.Y+3*scale,bounds.Width-6*scale,bounds.Height-6*scale),8*scale))
                {focus.DashStyle=DashStyle.Dot;g.DrawPath(focus,path);}
            }
        }
        protected override void OnMouseDown(MouseEventArgs e)
        {
            base.OnMouseDown(e);
            int index=IndexFromPoint(e.Location);
            if(Enabled&&e.Button==MouseButtons.Left&&index!=NoMatches)
            {
                SelectedIndex=index;
                DiscoveredTv tv=Items[index] as DiscoveredTv;
                // A paired TV hands focus to the editor from MainForm. Keep
                // the list focused for an unpaired row so Connect remains
                // the next keyboard action.
                if(tv==null||IsPairedDevice==null||!IsPairedDevice(tv))Focus();
            }
        }
        protected override void OnKeyDown(KeyEventArgs e)
        {
            if(Enabled&&Items.Count>0&&(e.KeyCode==Keys.Up||e.KeyCode==Keys.Down||e.KeyCode==Keys.Home||e.KeyCode==Keys.End||e.KeyCode==Keys.PageUp||e.KeyCode==Keys.PageDown))
            {
                int page=Math.Max(1,ClientSize.Height/ItemHeight);
                int next=e.KeyCode==Keys.Home?0:e.KeyCode==Keys.End?Items.Count-1:SelectedIndex+(e.KeyCode==Keys.Up?-1:e.KeyCode==Keys.Down?1:e.KeyCode==Keys.PageUp?-page:page);
                SelectedIndex=Math.Max(0,Math.Min(Items.Count-1,next));
                e.Handled=e.SuppressKeyPress=true;
            }
            base.OnKeyDown(e);
        }
        protected override void OnMouseMove(MouseEventArgs e)
        {
            base.OnMouseMove(e);
            int index=IndexFromPoint(e.Location);
            Cursor=Enabled&&index!=NoMatches?Cursors.Hand:Cursors.Default;
            if(index==hoverIndex)return;
            int previous=hoverIndex;hoverIndex=index;
            if(previous>=0&&previous<Items.Count)Invalidate(GetItemRectangle(previous));
            if(index>=0)Invalidate(GetItemRectangle(index));
        }
        protected override void OnMouseLeave(EventArgs e)
        {
            int previous=hoverIndex;hoverIndex=-1;Cursor=Cursors.Default;
            if(previous>=0&&previous<Items.Count)Invalidate(GetItemRectangle(previous));
            base.OnMouseLeave(e);
        }
        protected override void OnEnabledChanged(EventArgs e){hoverIndex=-1;Cursor=Cursors.Default;Invalidate();base.OnEnabledChanged(e);}
        protected override void OnResize(EventArgs e)
        {
            base.OnResize(e);
            if(Items.Count>0&&ItemHeight>0)
            {
                int maxTop=Math.Max(0,Items.Count-Math.Max(1,ClientSize.Height/ItemHeight));
                if(TopIndex>maxTop)TopIndex=maxTop;
            }
        }
        protected override void OnGotFocus(EventArgs e){base.OnGotFocus(e);Invalidate();}
        protected override void OnLostFocus(EventArgs e){base.OnLostFocus(e);Invalidate();}
        protected override void WndProc(ref Message m)
        {
            // The native owner-drawn ListBox otherwise erases the hovered row
            // before drawing its rounded background. Paint the full client in
            // one buffer, while keeping native selection, scrolling and accessibility.
            if(m.Msg==0x0014){m.Result=new IntPtr(1);return;}
            if(m.Msg==0x000F&&m.WParam==IntPtr.Zero)
            {
                PaintContext paint;
                IntPtr target=BeginPaint(Handle,out paint);
                try
                {
                    if(target!=IntPtr.Zero&&ClientSize.Width>0&&ClientSize.Height>0)
                    using(Graphics screen=Graphics.FromHdc(target))
                    using(BufferedGraphics buffer=BufferedGraphicsManager.Current.Allocate(screen,ClientRectangle))
                    {
                        buffer.Graphics.Clear(BackColor);
                        IntPtr canvas=buffer.Graphics.GetHdc();
                        try
                        {
                            Message print=Message.Create(Handle,0x0318,canvas,new IntPtr(0x0004|0x0008));
                            base.WndProc(ref print);
                        }
                        finally{buffer.Graphics.ReleaseHdc(canvas);}
                        DrawEmptyState(buffer.Graphics);
                        buffer.Render(screen);
                    }
                }
                finally{EndPaint(Handle,ref paint);}
                m.Result=IntPtr.Zero;return;
            }
            base.WndProc(ref m);
            bool printing=m.Msg==0x0317||m.Msg==0x0318;
            if(printing&&Items.Count==0)
            using(Graphics g=printing&&m.WParam!=IntPtr.Zero?Graphics.FromHdc(m.WParam):CreateGraphics())
                DrawEmptyState(g);
        }
        private void DrawEmptyState(Graphics g)
        {
            if(Items.Count==0&&!String.IsNullOrEmpty(EmptyMessage))
            {
                Color color=EmptyMessageError?Theme.Error:Theme.Muted;
                TextRenderer.DrawText(g,EmptyMessage,CaptionFont,Rectangle.Inflate(ClientRectangle,-12,-4),color,
                    TextFormatFlags.HorizontalCenter|TextFormatFlags.VerticalCenter|TextFormatFlags.WordBreak|TextFormatFlags.NoPrefix);
            }
        }
        [StructLayout(LayoutKind.Sequential)]
        private struct PaintContext
        {
            public IntPtr Hdc;
            public int Erase,Left,Top,Right,Bottom,Restore,IncUpdate;
            public int Reserved0,Reserved1,Reserved2,Reserved3,Reserved4,Reserved5,Reserved6,Reserved7;
        }
        [DllImport("user32.dll")]private static extern IntPtr BeginPaint(IntPtr window,out PaintContext paint);
        [DllImport("user32.dll")]private static extern bool EndPaint(IntPtr window,ref PaintContext paint);
        protected override void Dispose(bool disposing){if(disposing&&captionFont!=null){captionFont.Dispose();captionFont=null;}base.Dispose(disposing);}
    }
    internal sealed class TvBadge : Control
    {
        internal TvBadge(){SetStyle(ControlStyles.UserPaint|ControlStyles.OptimizedDoubleBuffer,true);Size=new Size(42,42);}
        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode=SmoothingMode.AntiAlias;
            e.Graphics.ScaleTransform(Width/42f,Height/42f);
            using(Brush b=new SolidBrush(Theme.IconSurface))e.Graphics.FillEllipse(b,0,0,41,41);
            Theme.Tv(e.Graphics,new RectangleF(10,12,22,22),Theme.Blue,1.7f);
        }
    }
    internal sealed class LinkArt : Control
    {
        internal LinkArt(){DoubleBuffered=true;Size=new Size(166,76);}
        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g=e.Graphics;g.SmoothingMode=SmoothingMode.AntiAlias;g.ScaleTransform(Width/166f,Height/76f);
            using(Brush soft=new SolidBrush(Theme.ArtSurface))g.FillEllipse(soft,35,1,124,70);
            using(Brush white=new SolidBrush(Theme.Surface))
            using(Pen pen=new Pen(Theme.Blue,2))
            using(GraphicsPath laptop=Theme.Round(new RectangleF(8,30,53,33),4))
            {g.FillPath(white,laptop);g.DrawPath(pen,laptop);g.DrawLine(pen,1,67,67,67);g.DrawLine(pen,19,42,43,42);g.DrawLine(pen,19,49,35,49);}
            Theme.Tv(g,new RectangleF(103,16,54,46),Theme.Blue,2);
            using(Pen dots=new Pen(Theme.Blue,2)){dots.DashStyle=DashStyle.Dot;g.DrawLine(dots,69,43,94,43);}
            using(Brush green=new SolidBrush(Theme.Green))g.FillEllipse(green,141,55,12,12);
        }
    }
}
