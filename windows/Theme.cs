using System;
using System.Drawing;
using System.IO;
using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace TvText
{
    internal static class Theme
    {
        private static bool dark;
        internal static string StoragePathOverride=null;
        private static string StoragePath{get{return StoragePathOverride??Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"TVText","theme.txt");}}
        internal static bool IsDark{get{return dark;}}
        internal static Color Ink{get{return dark?Color.FromArgb(235,239,249):Color.FromArgb(33,44,66);}}
        internal static Color Muted{get{return dark?Color.FromArgb(164,174,195):Color.FromArgb(114,128,150);}}
        internal static Color Blue{get{return dark?Color.FromArgb(170,184,255):Color.FromArgb(73,105,237);}}
        internal static Color ButtonInk{get{return dark?Blue:Color.FromArgb(61,90,219);}}
        internal static Color PrimaryFill{get{return dark?Color.FromArgb(79,99,213):Color.FromArgb(73,105,237);}}
        internal static Color PrimaryHover{get{return dark?Color.FromArgb(68,85,191):Color.FromArgb(57,87,218);}}
        internal static Color Canvas{get{return dark?Color.FromArgb(20,24,34):Color.FromArgb(245,247,252);}}
        internal static Color Surface{get{return dark?Color.FromArgb(31,37,51):Color.White;}}
        internal static Color Input{get{return dark?Color.FromArgb(25,30,42):Color.White;}}
        internal static Color Border{get{return dark?Color.FromArgb(59,68,87):Color.FromArgb(230,235,245);}}
        internal static Color SelectedBorder{get{return dark?Color.FromArgb(113,133,216):Color.FromArgb(166,184,249);}}
        internal static Color Row{get{return dark?Color.FromArgb(38,45,60):Color.FromArgb(249,250,253);}}
        internal static Color RowHover{get{return dark?Color.FromArgb(48,57,76):Color.FromArgb(245,247,252);}}
        internal static Color RowSelected{get{return dark?Color.FromArgb(45,56,91):Color.FromArgb(235,240,255);}}
        internal static Color IconSurface{get{return dark?Color.FromArgb(54,65,91):Color.FromArgb(235,240,253);}}
        internal static Color ArtSurface{get{return dark?Color.FromArgb(36,45,68):Color.FromArgb(232,238,255);}}
        internal static Color Green{get{return dark?Color.FromArgb(76,190,151):Color.FromArgb(41,155,119);}}
        internal static Color Error{get{return dark?Color.FromArgb(246,156,144):Color.FromArgb(186,83,66);}}
        internal static Color Button{get{return dark?Color.FromArgb(48,57,76):Color.FromArgb(241,244,252);}}
        internal static Color ButtonHover{get{return dark?Color.FromArgb(61,73,98):Color.FromArgb(231,237,252);}}
        internal static Color DisabledButton{get{return dark?Color.FromArgb(48,54,70):Color.FromArgb(231,235,247);}}
        static Theme(){Reload();}
        internal static void Reload()
        {
            try{dark=File.Exists(StoragePath)&&File.ReadAllText(StoragePath).Trim().Equals("dark",StringComparison.OrdinalIgnoreCase);}catch{dark=false;}
        }
        internal static void Toggle()
        {
            dark=!dark;
            try
            {
                string directory=Path.GetDirectoryName(StoragePath);
                if(!Directory.Exists(directory))Directory.CreateDirectory(directory);
                File.WriteAllText(StoragePath,dark?"dark":"light");
            }
            catch{}
        }
        // Windows owns the non-client title bar; match it to the selected
        // application theme where DWM supports dark title bars.
        internal static void ApplyTitleBar(Form form)
        {
            if(!form.IsHandleCreated)return;
            try
            {
                int enabled=dark?1:0;
                if(DwmSetWindowAttribute(form.Handle,20,ref enabled,4)!=0)
                    DwmSetWindowAttribute(form.Handle,19,ref enabled,4);
            }
            catch(DllNotFoundException){}
            catch(EntryPointNotFoundException){}
        }
        [DllImport("dwmapi.dll")]private static extern int DwmSetWindowAttribute(IntPtr window,int attribute,ref int value,int size);
        internal static void ApplyNativeControl(Control control)
        {
            if(!control.IsHandleCreated)return;
            try{SetWindowTheme(control.Handle,dark?"DarkMode_Explorer":"Explorer",null);}
            catch(DllNotFoundException){}
            catch(EntryPointNotFoundException){}
        }
        [DllImport("uxtheme.dll",CharSet=CharSet.Unicode)]private static extern int SetWindowTheme(IntPtr window,string application,string classes);
        internal static System.Drawing.Drawing2D.GraphicsPath Round(RectangleF r,float radius)
        {
            float d=radius*2;System.Drawing.Drawing2D.GraphicsPath p=new System.Drawing.Drawing2D.GraphicsPath();
            p.AddArc(r.X,r.Y,d,d,180,90);p.AddArc(r.Right-d,r.Y,d,d,270,90);
            p.AddArc(r.Right-d,r.Bottom-d,d,d,0,90);p.AddArc(r.X,r.Bottom-d,d,d,90,90);p.CloseFigure();return p;
        }
        internal static void Tv(Graphics g,RectangleF r,Color color,float width)
        {
            using(Pen pen=new Pen(color,width))
            using(System.Drawing.Drawing2D.GraphicsPath path=Round(new RectangleF(r.X,r.Y,r.Width,r.Height*.7f),4))
            {
                g.DrawPath(pen,path);float middle=r.X+r.Width/2;
                g.DrawLine(pen,middle,r.Y+r.Height*.72f,middle,r.Y+r.Height*.9f);
                g.DrawLine(pen,middle-r.Width*.2f,r.Y+r.Height*.9f,middle+r.Width*.2f,r.Y+r.Height*.9f);
            }
        }
    }
}
