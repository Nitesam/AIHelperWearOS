"""
AI Helper WearOS Chat Analyzer
GUI application for analyzing chat history - VS Code Dark Theme
"""

import tkinter as tk
from tkinter import ttk, filedialog, messagebox, scrolledtext
import json
import subprocess
import os
from datetime import datetime
import threading
import re


class ChatAnalyzer:
    def __init__(self, root):
        self.root = root
        self.root.title("AI Helper WearOS - Chat Analyzer")
        self.root.geometry("1000x750")

        self.colors = {
            "bg": "#1e1e1e",
            "sidebar": "#252526",
            "editor": "#1e1e1e",
            "border": "#3c3c3c",
            "text": "#d4d4d4",
            "text_dim": "#808080",
            "accent": "#0e639c",
            "accent_hover": "#1177bb",
            "green": "#4ec9b0",
            "blue": "#569cd6",
            "orange": "#ce9178",
            "yellow": "#dcdcaa",
            "purple": "#c586c0",
            "selection": "#264f78"
        }

        self.root.configure(bg=self.colors["bg"])

        self.chat_data = None
        self.current_session_messages = []

        self.setup_styles()
        self.create_widgets()

    def setup_styles(self):
        style = ttk.Style()
        style.theme_use("clam")

        c = self.colors
        style.configure("TFrame", background=c["bg"])
        style.configure("Sidebar.TFrame", background=c["sidebar"])
        style.configure("TLabel", background=c["bg"], foreground=c["text"], font=("Consolas", 10))
        style.configure("Header.TLabel", font=("Consolas", 12, "bold"), foreground=c["blue"])
        style.configure("Stats.TLabel", font=("Consolas", 10), foreground=c["text_dim"])
        style.configure("TButton", background=c["accent"], foreground="white", font=("Consolas", 9))
        style.map("TButton", background=[("active", c["accent_hover"])])

        style.configure("Treeview",
                       background=c["sidebar"],
                       foreground=c["text"],
                       fieldbackground=c["sidebar"],
                       font=("Consolas", 9))
        style.configure("Treeview.Heading",
                       background=c["border"],
                       foreground=c["text"],
                       font=("Consolas", 9, "bold"))
        style.map("Treeview", background=[("selected", c["selection"])])

        style.configure("TLabelframe", background=c["bg"], foreground=c["text"])
        style.configure("TLabelframe.Label", background=c["bg"], foreground=c["blue"], font=("Consolas", 10))

    def create_widgets(self):
        c = self.colors

        # Main container
        main_frame = ttk.Frame(self.root, padding=10)
        main_frame.pack(fill=tk.BOTH, expand=True)

        # Header
        header_frame = ttk.Frame(main_frame)
        header_frame.pack(fill=tk.X, pady=(0, 10))

        header = ttk.Label(header_frame, text="📱 AI Helper WearOS - Chat Analyzer", style="Header.TLabel")
        header.pack(side=tk.LEFT)

        # ADB Controls
        adb_frame = ttk.Frame(main_frame, style="Sidebar.TFrame", padding=8)
        adb_frame.pack(fill=tk.X, pady=(0, 10))

        # Device row
        ttk.Label(adb_frame, text="Device:", background=c["sidebar"]).pack(side=tk.LEFT, padx=(0, 8))
        self.device_combo = ttk.Combobox(adb_frame, width=30, state="readonly", font=("Consolas", 9))
        self.device_combo.pack(side=tk.LEFT, padx=(0, 8))

        refresh_btn = ttk.Button(adb_frame, text="🔄 Refresh", command=self.refresh_devices)
        refresh_btn.pack(side=tk.LEFT, padx=3)

        retrieve_btn = ttk.Button(adb_frame, text="📥 Retrieve & Clean", command=self.retrieve_from_device)
        retrieve_btn.pack(side=tk.LEFT, padx=3)

        load_btn = ttk.Button(adb_frame, text="📂 Load File", command=self.load_json_file)
        load_btn.pack(side=tk.LEFT, padx=3)

        # Status
        self.status_var = tk.StringVar(value="Ready")
        status_label = ttk.Label(adb_frame, textvariable=self.status_var, style="Stats.TLabel", background=c["sidebar"])
        status_label.pack(side=tk.RIGHT, padx=10)

        # Statistics bar
        stats_frame = ttk.Frame(main_frame, style="Sidebar.TFrame", padding=8)
        stats_frame.pack(fill=tk.X, pady=(0, 10))

        self.stats_labels = {}
        stats_config = [
            ("Sessions", "0", c["green"]),
            ("Messages", "0", c["yellow"]),
            ("Avg Response", "0 chars", c["orange"]),
            ("Models", "-", c["purple"])
        ]

        for name, default, color in stats_config:
            frame = ttk.Frame(stats_frame, style="Sidebar.TFrame")
            frame.pack(side=tk.LEFT, expand=True, padx=15)
            ttk.Label(frame, text=name, style="Stats.TLabel", background=c["sidebar"]).pack()
            lbl = tk.Label(frame, text=default, font=("Consolas", 14, "bold"), fg=color, bg=c["sidebar"])
            lbl.pack()
            self.stats_labels[name] = lbl

        # Content paned window
        content_pane = tk.PanedWindow(main_frame, orient=tk.HORIZONTAL, bg=c["border"], sashwidth=4)
        content_pane.pack(fill=tk.BOTH, expand=True)

        # Left panel - Sessions
        left_frame = tk.Frame(content_pane, bg=c["sidebar"])
        content_pane.add(left_frame, width=350)

        sessions_header = tk.Frame(left_frame, bg=c["sidebar"])
        sessions_header.pack(fill=tk.X, padx=5, pady=5)
        tk.Label(sessions_header, text="📋 Sessions", font=("Consolas", 10, "bold"),
                fg=c["blue"], bg=c["sidebar"]).pack(side=tk.LEFT)

        self.sessions_tree = ttk.Treeview(left_frame,
                                         columns=("title", "model", "date", "msgs"),
                                         show="headings",
                                         height=20)
        self.sessions_tree.heading("title", text="Title")
        self.sessions_tree.heading("model", text="Model")
        self.sessions_tree.heading("date", text="Date")
        self.sessions_tree.heading("msgs", text="#")
        self.sessions_tree.column("title", width=120)
        self.sessions_tree.column("model", width=80)
        self.sessions_tree.column("date", width=80)
        self.sessions_tree.column("msgs", width=30)
        self.sessions_tree.pack(fill=tk.BOTH, expand=True, padx=5, pady=(0, 5))
        self.sessions_tree.bind("<<TreeviewSelect>>", self.on_session_select)

        # Scrollbar
        scroll = ttk.Scrollbar(left_frame, orient=tk.VERTICAL, command=self.sessions_tree.yview)
        self.sessions_tree.configure(yscrollcommand=scroll.set)

        # Right panel - Messages
        right_frame = tk.Frame(content_pane, bg=c["bg"])
        content_pane.add(right_frame)

        # Messages header with copy button
        msg_header = tk.Frame(right_frame, bg=c["bg"])
        msg_header.pack(fill=tk.X, padx=5, pady=5)

        tk.Label(msg_header, text="💬 Messages", font=("Consolas", 10, "bold"),
                fg=c["blue"], bg=c["bg"]).pack(side=tk.LEFT)

        copy_btn = tk.Button(msg_header, text="📋 Copy JSON", font=("Consolas", 9),
                            bg=c["accent"], fg="white", relief=tk.FLAT,
                            command=self.copy_session_json)
        copy_btn.pack(side=tk.RIGHT, padx=5)

        # Messages text
        self.messages_text = scrolledtext.ScrolledText(
            right_frame,
            wrap=tk.WORD,
            bg=c["editor"],
            fg=c["text"],
            font=("Consolas", 10),
            insertbackground="white",
            relief=tk.FLAT,
            padx=10,
            pady=10
        )
        self.messages_text.pack(fill=tk.BOTH, expand=True, padx=5, pady=(0, 5))

        # Configure tags
        self.messages_text.tag_configure("user", foreground=c["green"], font=("Consolas", 10, "bold"))
        self.messages_text.tag_configure("assistant", foreground=c["blue"], font=("Consolas", 10, "bold"))
        self.messages_text.tag_configure("system", foreground=c["orange"], font=("Consolas", 10, "italic"))
        self.messages_text.tag_configure("timestamp", foreground=c["text_dim"])
        self.messages_text.tag_configure("latex", foreground=c["yellow"], font=("Consolas", 10))
        self.messages_text.tag_configure("separator", foreground=c["border"])

        # Initial refresh
        self.root.after(500, self.refresh_devices)

    def refresh_devices(self):
        try:
            result = subprocess.run(["adb", "devices"], capture_output=True, text=True, timeout=5)
            devices = []
            for line in result.stdout.strip().split("\n")[1:]:
                if "\tdevice" in line:
                    devices.append(line.split("\t")[0])

            self.device_combo["values"] = devices
            if devices:
                self.device_combo.current(0)
                self.status_var.set(f"✓ {len(devices)} device(s) found")
            else:
                self.status_var.set("⚠ No devices")
        except FileNotFoundError:
            self.status_var.set("✗ ADB not found")
        except Exception as e:
            self.status_var.set(f"✗ {str(e)[:30]}")

    def retrieve_from_device(self):
        device = self.device_combo.get()
        if not device:
            messagebox.showwarning("Warning", "No device selected")
            return

        self.status_var.set("⏳ Retrieving...")

        def do_retrieve():
            try:
                remote_path = "/sdcard/Download/aihelper_chat_export.json"
                local_path = os.path.join(os.getcwd(), "chat_export.json")

                pull = subprocess.run(["adb", "-s", device, "pull", remote_path, local_path],
                                      capture_output=True, text=True, timeout=30)

                if pull.returncode != 0:
                    self.root.after(0, lambda: self.status_var.set("⚠ No export found"))
                    self.root.after(0, lambda: messagebox.showinfo("Info",
                        "Export file not found.\n\nIn the app: Settings → 📤 Export Chat"))
                    return

                subprocess.run(["adb", "-s", device, "shell", "rm", remote_path],
                              capture_output=True, timeout=10)

                self.root.after(0, lambda: self.load_json_from_path(local_path))
                self.root.after(0, lambda: self.status_var.set(f"✓ Retrieved from {device[:15]}"))

            except Exception as e:
                self.root.after(0, lambda: self.status_var.set(f"✗ {str(e)[:25]}"))

        threading.Thread(target=do_retrieve, daemon=True).start()

    def load_json_file(self):
        filepath = filedialog.askopenfilename(
            title="Select Chat Export",
            filetypes=[("JSON", "*.json"), ("All", "*.*")]
        )
        if filepath:
            self.load_json_from_path(filepath)

    def load_json_from_path(self, filepath):
        try:
            with open(filepath, "r", encoding="utf-8") as f:
                self.chat_data = json.load(f)
            self.update_statistics()
            self.populate_sessions()
            self.status_var.set(f"✓ Loaded {os.path.basename(filepath)}")
        except Exception as e:
            messagebox.showerror("Error", f"Failed to load: {e}")

    def update_statistics(self):
        if not self.chat_data:
            return

        sessions = self.chat_data.get("sessions", [])
        messages = self.chat_data.get("messages", [])
        ai_msgs = [m for m in messages if m.get("role") == "assistant"]
        avg_len = sum(len(m.get("content", "")) for m in ai_msgs) // max(len(ai_msgs), 1)
        models = set(s.get("modelId", "?").split("/")[-1][:10] for s in sessions)

        self.stats_labels["Sessions"].config(text=str(len(sessions)))
        self.stats_labels["Messages"].config(text=str(len(messages)))
        self.stats_labels["Avg Response"].config(text=f"{avg_len}")
        self.stats_labels["Models"].config(text=", ".join(models)[:20] if models else "-")

    def populate_sessions(self):
        self.sessions_tree.delete(*self.sessions_tree.get_children())
        if not self.chat_data:
            return

        sessions = self.chat_data.get("sessions", [])
        messages = self.chat_data.get("messages", [])

        for s in sorted(sessions, key=lambda x: x.get("timestamp", 0), reverse=True):
            sid = s.get("id")
            title = s.get("title", "Untitled")[:20]
            model = s.get("modelId", "").split("/")[-1][:12]
            ts = s.get("timestamp", 0)
            date = datetime.fromtimestamp(ts / 1000).strftime("%m/%d %H:%M") if ts else "-"
            count = len([m for m in messages if m.get("sessionId") == sid])
            self.sessions_tree.insert("", tk.END, iid=str(sid), values=(title, model, date, count))

    def on_session_select(self, event):
        sel = self.sessions_tree.selection()
        if sel:
            self.display_session_messages(int(sel[0]))

    def display_session_messages(self, session_id):
        self.messages_text.config(state=tk.NORMAL)
        self.messages_text.delete(1.0, tk.END)

        if not self.chat_data:
            return

        messages = self.chat_data.get("messages", [])
        self.current_session_messages = sorted(
            [m for m in messages if m.get("sessionId") == session_id],
            key=lambda m: m.get("timestamp", 0)
        )

        for msg in self.current_session_messages:
            role = msg.get("role", "?")
            content = msg.get("content", "")
            ts = msg.get("timestamp", 0)
            time_str = datetime.fromtimestamp(ts / 1000).strftime("%H:%M:%S") if ts else ""

            # Role header
            icons = {"user": "👤 You", "assistant": "🤖 AI", "system": "⚙️ System"}
            self.messages_text.insert(tk.END, f"\n{icons.get(role, role)} ", role)
            self.messages_text.insert(tk.END, f"[{time_str}]\n", "timestamp")

            # Content with LaTeX highlighting
            self.insert_with_latex(content)
            self.messages_text.insert(tk.END, "\n" + "─" * 60 + "\n", "separator")

        self.messages_text.config(state=tk.DISABLED)

    def insert_with_latex(self, content):
        """Insert text with LaTeX formulas highlighted"""
        # Pattern for LaTeX: $...$, $$...$$, \[...\], \(...\)
        latex_pattern = r'(\$\$.*?\$\$|\$.*?\$|\\\[.*?\\\]|\\\(.*?\\\))'

        parts = re.split(latex_pattern, content, flags=re.DOTALL)
        for part in parts:
            if re.match(latex_pattern, part, re.DOTALL):
                self.messages_text.insert(tk.END, part, "latex")
            else:
                self.messages_text.insert(tk.END, part)

    def copy_session_json(self):
        """Copy current session messages as JSON to clipboard"""
        if not self.current_session_messages:
            messagebox.showinfo("Info", "No session selected")
            return

        json_str = json.dumps(self.current_session_messages, indent=2, ensure_ascii=False)
        self.root.clipboard_clear()
        self.root.clipboard_append(json_str)
        self.status_var.set("✓ Copied to clipboard")


def main():
    root = tk.Tk()
    app = ChatAnalyzer(root)
    root.mainloop()


if __name__ == "__main__":
    main()
