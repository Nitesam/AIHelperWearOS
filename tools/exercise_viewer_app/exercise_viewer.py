"""
Visualizzatore Esercizi Analisi 2 con rendering LaTeX via QtWebEngine
Autore: AIHelperWearOS Tools
"""

import json
import sys
import os
from PyQt5.QtWidgets import (QApplication, QMainWindow, QWidget, QVBoxLayout, 
                             QHBoxLayout, QListWidget, QListWidgetItem, QPushButton,
                             QLabel, QComboBox, QSplitter, QFrame)
from PyQt5.QtWebEngineWidgets import QWebEngineView
from PyQt5.QtCore import Qt, QUrl
from PyQt5.QtGui import QFont, QPalette, QColor

# HTML template con MathJax per rendering LaTeX
HTML_TEMPLATE = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <script src="https://polyfill.io/v3/polyfill.min.js?features=es6"></script>
    <script id="MathJax-script" async src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"></script>
    <style>
        body {{
            background-color: #1a1a2e;
            color: #e0e0e0;
            font-family: 'Segoe UI', Arial, sans-serif;
            font-size: 14px;
            padding: 20px;
            line-height: 1.6;
        }}
        .section-title {{
            color: #00d4ff;
            font-size: 18px;
            font-weight: bold;
            margin-top: 20px;
            margin-bottom: 10px;
            border-bottom: 2px solid #0f3460;
            padding-bottom: 5px;
        }}
        .phase {{
            color: #4fc3f7;
            font-weight: bold;
            margin-top: 15px;
        }}
        .result {{
            background: linear-gradient(135deg, #0f3460 0%, #16213e 100%);
            border: 2px solid #00d4ff;
            border-radius: 8px;
            padding: 15px;
            margin: 15px 0;
        }}
        .result-label {{
            color: #00ff88;
            font-weight: bold;
        }}
        .testo-box {{
            background-color: #16213e;
            border-left: 4px solid #e94560;
            padding: 15px;
            margin: 10px 0;
            border-radius: 0 8px 8px 0;
        }}
        .svolgimento-box {{
            background-color: #16213e;
            padding: 15px;
            margin: 10px 0;
            border-radius: 8px;
        }}
        mjx-container {{
            color: #ffcc00 !important;
        }}
    </style>
</head>
<body>
{content}
</body>
</html>
"""


class ExerciseViewer(QMainWindow):
    def __init__(self, json_path):
        super().__init__()
        self.exercises = []
        self.categories = {}
        self.current_exercise = None
        
        self.load_exercises(json_path)
        self.setup_ui()
        
    def load_exercises(self, json_path):
        try:
            with open(json_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
            
            self.exercises = data.get('exercises', [])
            
            for ex in self.exercises:
                cat = ex.get('categoria', 'Altro')
                if cat not in self.categories:
                    self.categories[cat] = []
                self.categories[cat].append(ex)
                
            print(f"✅ Caricati {len(self.exercises)} esercizi in {len(self.categories)} categorie")
            
        except Exception as e:
            print(f"❌ Errore caricamento: {e}")
            
    def setup_ui(self):
        self.setWindowTitle("📚 Visualizzatore Esercizi Analisi 2 - LaTeX")
        self.setGeometry(100, 100, 1400, 900)
        self.setStyleSheet("""
            QMainWindow { background-color: #1a1a2e; }
            QLabel { color: white; font-size: 12px; }
            QListWidget { 
                background-color: #16213e; 
                color: white; 
                border: 1px solid #0f3460;
                border-radius: 5px;
                font-size: 11px;
            }
            QListWidget::item:selected { 
                background-color: #e94560; 
            }
            QListWidget::item:hover { 
                background-color: #0f3460; 
            }
            QComboBox { 
                background-color: #16213e; 
                color: white; 
                border: 1px solid #0f3460;
                padding: 5px;
                border-radius: 5px;
            }
            QPushButton { 
                background-color: #0f3460; 
                color: white; 
                border: none;
                padding: 8px 15px;
                border-radius: 5px;
                font-weight: bold;
            }
            QPushButton:hover { 
                background-color: #e94560; 
            }
            QTextBrowser {
                background-color: #1a1a2e;
                border: none;
            }
        """)
        
        # Widget centrale
        central = QWidget()
        self.setCentralWidget(central)
        layout = QHBoxLayout(central)
        
        # Pannello sinistro
        left_panel = QFrame()
        left_panel.setMaximumWidth(350)
        left_layout = QVBoxLayout(left_panel)
        
        # Titolo
        title = QLabel("📋 Esercizi")
        title.setStyleSheet("font-size: 16px; font-weight: bold; color: #00d4ff;")
        left_layout.addWidget(title)
        
        # Filtro categoria
        self.category_combo = QComboBox()
        self.category_combo.addItem("Tutte le categorie")
        self.category_combo.addItems(list(self.categories.keys()))
        self.category_combo.currentTextChanged.connect(self.filter_exercises)
        left_layout.addWidget(self.category_combo)
        
        # Lista esercizi
        self.exercise_list = QListWidget()
        self.exercise_list.itemClicked.connect(self.on_exercise_selected)
        left_layout.addWidget(self.exercise_list)
        
        self.populate_list()
        
        # Pulsanti navigazione
        nav_layout = QHBoxLayout()
        prev_btn = QPushButton("⬅️ Prec")
        prev_btn.clicked.connect(self.prev_exercise)
        next_btn = QPushButton("➡️ Succ")
        next_btn.clicked.connect(self.next_exercise)
        copy_btn = QPushButton("📋 Copia")
        copy_btn.clicked.connect(self.copy_latex)
        nav_layout.addWidget(prev_btn)
        nav_layout.addWidget(next_btn)
        nav_layout.addWidget(copy_btn)
        left_layout.addLayout(nav_layout)
        
        layout.addWidget(left_panel)
        
        # Pannello destro - WebEngine per MathJax
        self.browser = QWebEngineView()
        self.browser.setHtml(HTML_TEMPLATE.format(content="<p>Seleziona un esercizio dalla lista</p>"))
        layout.addWidget(self.browser, stretch=1)
        
    def populate_list(self, filter_cat=None):
        self.exercise_list.clear()
        for ex in self.exercises:
            cat = ex.get('categoria', 'Altro')
            if filter_cat and filter_cat != "Tutte le categorie" and cat != filter_cat:
                continue
            item = QListWidgetItem(f"{ex.get('id', '?')} - {ex.get('sottotipo', '')[:25]}")
            item.setData(Qt.UserRole, ex.get('id'))
            self.exercise_list.addItem(item)
            
    def filter_exercises(self, category):
        self.populate_list(category)
        
    def on_exercise_selected(self, item):
        exercise_id = item.data(Qt.UserRole)
        exercise = next((ex for ex in self.exercises if ex.get('id') == exercise_id), None)
        if exercise:
            self.display_exercise(exercise)
            
    def display_exercise(self, exercise):
        self.current_exercise = exercise
        
        testo = exercise.get('testo', 'Nessun testo')
        svolgimento = exercise.get('svolgimento', 'Nessuno svolgimento')
        
        # Formatta contenuto HTML
        content = f"""
        <div class="section-title">📝 TESTO</div>
        <div class="testo-box">
            {self.format_latex_html(testo)}
        </div>
        
        <div class="section-title">✏️ SVOLGIMENTO</div>
        <div class="svolgimento-box">
            {self.format_latex_html(svolgimento)}
        </div>
        """
        
        html = HTML_TEMPLATE.format(content=content)
        self.browser.setHtml(html)
        
    def format_latex_html(self, text):
        """Formatta il testo per HTML con LaTeX"""
        if not text:
            return "<p>Nessun contenuto</p>"
            
        result = text
        
        # Escape HTML (ma NON i backslash del LaTeX)
        result = result.replace('&', '&amp;')
        result = result.replace('<', '&lt;')
        result = result.replace('>', '&gt;')
        
        # Prima gestisci $$...$$ (display math) con placeholder
        import re
        
        # Salva le formule display in un placeholder per non confonderle con inline
        display_formulas = []
        def save_display(match):
            display_formulas.append(match.group(1))
            return f"__DISPLAY_{len(display_formulas)-1}__"
        
        result = re.sub(r'\$\$(.+?)\$\$', save_display, result, flags=re.DOTALL)
        
        # Ora converti le formule inline $...$ in \(...\)
        result = re.sub(r'\$([^$]+?)\$', r'\\(\1\\)', result)
        
        # Ripristina le formule display come \[...\]
        for i, formula in enumerate(display_formulas):
            result = result.replace(f"__DISPLAY_{i}__", f"\\[{formula}\\]")
        
        # Converti newline in <br>
        result = result.replace('\\n', '<br>')
        result = result.replace('\n', '<br>')
        
        # Evidenzia sezioni
        result = result.replace('PROBLEMA:', '<div class="phase">PROBLEMA:</div>')
        result = result.replace('FASE 1:', '<div class="phase">FASE 1:</div>')
        result = result.replace('FASE 2:', '<div class="phase">FASE 2:</div>')
        result = result.replace('FASE 3:', '<div class="phase">FASE 3:</div>')
        result = result.replace('FASE 4:', '<div class="phase">FASE 4:</div>')
        result = result.replace('FASE 5:', '<div class="phase">FASE 5:</div>')
        result = result.replace('FASE 6:', '<div class="phase">FASE 6:</div>')
        
        # Evidenzia risposta
        if 'RISPOSTA:' in result:
            parts = result.split('RISPOSTA:')
            if len(parts) == 2:
                result = parts[0] + '<div class="result"><span class="result-label">✅ RISPOSTA:</span><br>' + parts[1] + '</div>'
                
        return result
        
    def prev_exercise(self):
        current = self.exercise_list.currentRow()
        if current > 0:
            self.exercise_list.setCurrentRow(current - 1)
            self.on_exercise_selected(self.exercise_list.currentItem())
            
    def next_exercise(self):
        current = self.exercise_list.currentRow()
        if current < self.exercise_list.count() - 1:
            self.exercise_list.setCurrentRow(current + 1)
            self.on_exercise_selected(self.exercise_list.currentItem())
            
    def copy_latex(self):
        if not self.current_exercise:
            return
        latex = f"% {self.current_exercise.get('id', '')}\n"
        latex += f"TESTO:\n{self.current_exercise.get('testo', '')}\n\n"
        latex += f"SVOLGIMENTO:\n{self.current_exercise.get('svolgimento', '')}"
        QApplication.clipboard().setText(latex)


def main():
    if getattr(sys, 'frozen', False):
        base_path = sys._MEIPASS
    else:
        base_path = os.path.dirname(os.path.abspath(__file__))
    
    script_dir = os.path.dirname(os.path.abspath(__file__))
    
    possible_paths = [
        os.path.join(base_path, 'esercizi_analisi2.json'),
        os.path.join(script_dir, 'esercizi_analisi2.json'),
        os.path.join(script_dir, '..', 'app', 'src', 'main', 'res', 'raw', 'esercizi_analisi2.json'),
        r'c:\Users\Nitesam\AndroidStudioProjects\AIHelperWearOS\app\src\main\res\raw\esercizi_analisi2.json'
    ]
    
    json_path = None
    for path in possible_paths:
        if os.path.exists(path):
            json_path = path
            break
            
    if not json_path:
        print("❌ File non trovato!")
        sys.exit(1)
        
    print(f"📂 Caricamento da: {json_path}")
    
    app = QApplication(sys.argv)
    viewer = ExerciseViewer(json_path)
    viewer.show()
    sys.exit(app.exec_())


if __name__ == "__main__":
    main()
