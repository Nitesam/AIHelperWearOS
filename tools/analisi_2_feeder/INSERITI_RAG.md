# Inserimenti nel DB RAG (Analisi 2 Feeder)

Data aggiornamento: 2026-02-16
File aggiornato: `app/src/main/res/raw/esercizi_analisi2.json`

## Esercizi inseriti (copiati dalle immagini allegate)

1. **ID:** EX-INT-IMG-001  
   **Sorgente:** `tools/analisi_2_feeder/integrale_indefinito.jpeg`  
   **Testo esercizio:**
   - Integrale indefinito: `∫ cosx (sin^3x - 4cos^2x + sinx) dx`

2. **ID:** EX-INT-IMG-002  
   **Sorgente:** `tools/analisi_2_feeder/integrale_indefinito_2.jpeg`  
   **Testo esercizio:**
   - Integrale indefinito: `∫ (log^2x + 2) / (x logx (log^2x + 8)) dx`

3. **ID:** EX-INT-IMG-003  
   **Sorgente:** immagini allegate in chat (2 pagine)  
   **Nota sorgente:** la seconda immagine è la continuazione della prima (un unico esercizio)  
   **Testo esercizio:**
   - Trovare `F(x)=∫f(x)dx` tale che `F(-1)=7/6` con `f(x)=x^2-|x-1|+2x+3`

4. **ID:** EX-INT-IMG-004  
   **Sorgente:** immagini allegate in chat (prima foto del nuovo invio)  
   **Testo esercizio:**
   - Risolvere: `y'' - y' - 2y = e^(3x)(2x+1)`

5. **ID:** EX-INT-IMG-005  
   **Sorgente:** immagini allegate in chat (seconda + terza foto del nuovo invio)  
   **Nota sorgente:** la terza immagine è la continuazione della seconda (un unico esercizio)  
   **Testo esercizio:**
   - Risolvere il problema: `{ y' + y/x = log(x^2+4),  y(√(e^2-4)) = √(e^2-4) }`

6. **ID:** EX-INT-IMG-006  
   **Sorgente:** immagini allegate in chat (nuovo invio: foto 1 + foto 2)  
   **Nota sorgente:** la seconda immagine è la continuazione della prima (un unico esercizio)  
   **Testo esercizio:**
   - Risolvere il problema: `{ y'' - 2y' = cos x,  y(π/2) = 3/5,  y'(π/2) = 0 }`

7. **ID:** EX-INT-IMG-007  
   **Sorgente:** immagini allegate in chat (batch successivo, foto 1 - parte alta)  
   **Testo esercizio:**
   - Studiare e sommare: `Σ ((2^n + 3^n)/6^n)`

8. **ID:** EX-INT-IMG-008  
   **Sorgente:** immagini allegate in chat (batch successivo, foto 1 - parte bassa)  
   **Nota sorgente:** nella stessa foto è presente un esercizio diverso rispetto alla parte alta  
   **Testo esercizio:**
   - Studiare: `Σ ((√(n+1)-√n)/√n)`

9. **ID:** EX-INT-IMG-009  
   **Sorgente:** immagini allegate in chat (batch successivo, foto 2 - parte alta)  
   **Testo esercizio:**
   - Studiare: `Σ (n!)^2/(2n)!`

10. **ID:** EX-INT-IMG-010  
    **Sorgente:** immagini allegate in chat (batch successivo, foto 2 - parte bassa)  
    **Nota sorgente:** nella stessa foto è presente un esercizio diverso rispetto alla parte alta  
    **Testo esercizio:**
    - Studiare: `Σ ((n^2+1)/(2n^2+3))^(n^2)`

11. **ID:** EX-INT-IMG-011  
    **Sorgente:** immagini allegate in chat (batch successivo, foto 3)  
    **Testo esercizio:**
    - Studiare e stimare errore: `Σ (-1)^(n+1)/n^3` con richiesta `|R_n| < 1/100`

12. **ID:** EX-INT-IMG-012  
    **Sorgente:** immagini allegate in chat (batch successivo, foto 4)  
    **Testo esercizio:**
    - Calcolare: `lim_(x,y→0,0) x^3 y / (x^4 + y^2)`

13. **ID:** EX-INT-IMG-013  
    **Sorgente:** immagini allegate in chat (batch successivo, foto 5)  
    **Testo esercizio:**
    - Studiare la differenziabilità in `R^2` di: `f(x,y)=|x|y+x^2`

14. **ID:** EX-INT-IMG-014  
    **Sorgente:** immagini allegate in chat (batch successivo, foto 6)  
    **Testo esercizio:**
    - Per `f(x,y)=x^3-3xy+y^2`, trovare gradiente, derivata direzionale su `3x-4y+1=0`, direzione di massima crescita e valore massimo

15. **ID:** EX-INT-IMG-015  
    **Sorgente:** immagini allegate in chat (batch successivo, foto 7)  
    **Testo esercizio:**
    - Determinare e classificare i punti stazionari di: `f(x,y)=x^3+3xy^2-15x-12y`

## Note
- Trascrizione effettuata direttamente dalle immagini fornite in chat.
- Contenuto inserito in campo `svolgimento` con struttura testuale fedele ai passaggi presenti nei fogli.
- Nel batch piu' recente sono stati corretti i refusi matematici evidenti (indici/segni/conti) mantenendo stile e livello di dettaglio.
