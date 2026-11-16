using System;
using System.Runtime.InteropServices;
using System.Text;

// Clash Verge 2.5.0: global-hotkey 0.7.0, keyboard-types 0.7.0.
// Invoke Verge's own toggle_system_proxy through a configured native shortcut.
namespace CodexPocket {
    public static class ClashNativeControl {
        private delegate bool EnumWindowProc(IntPtr window, IntPtr parameter);
        [DllImport("user32.dll")] private static extern bool EnumWindows(EnumWindowProc callback, IntPtr parameter);
        [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetClassName(IntPtr window, StringBuilder name, int capacity);
        [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr window, out uint processId);
        [DllImport("user32.dll", SetLastError = true)] private static extern bool PostMessage(IntPtr window, uint message, IntPtr wParam, IntPtr lParam);
        [DllImport("user32.dll", SetLastError = true)] private static extern bool PostThreadMessage(uint threadId, uint message, IntPtr wParam, IntPtr lParam);
        public static IntPtr FindHotkeyWindow(int processId) {
            IntPtr found = IntPtr.Zero;
            EnumWindows(delegate(IntPtr window, IntPtr unused) {
                uint owner; GetWindowThreadProcessId(window, out owner);
                if (owner != processId) return true;
                var name = new StringBuilder(256); GetClassName(window, name, name.Capacity);
                if (name.ToString() != "global_hotkey_app") return true;
                found = window; return false;
            }, IntPtr.Zero);
            return found;
        }
        public static void Toggle(IntPtr window) {
            if (window == IntPtr.Zero) throw new InvalidOperationException("native_unavailable");
            // HotKey::id = (Modifiers::bits << 16) | Code::F24.
            // ALT=1, CONTROL=8, SHIFT=512; F24 enum index=183.
            const int shortcutId = (521 << 16) | 183;
            const int keyboardMessage = (0x87 << 16) | 7;
            if (!PostMessage(window, 0x0312, new IntPtr(shortcutId), new IntPtr(keyboardMessage)))
                throw new InvalidOperationException("native_unavailable");
        }
        public static void RequestExit(IntPtr window) {
            uint owner; uint thread = GetWindowThreadProcessId(window, out owner);
            if (thread == 0 || !PostThreadMessage(thread, 0x0012, IntPtr.Zero, IntPtr.Zero))
                throw new InvalidOperationException("Could not request Clash Verge exit.");
        }
    }
}
