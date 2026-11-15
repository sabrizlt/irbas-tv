# Irbas TV

Una schermata principale semplice per **Android TV** e **Google TV**: niente pubblicità, solo le tue app,
il tasto Home che riporta sempre lì, un assistente a voce e un telecomando sul telefono (pagina web, si aggiunge alla schermata Home).

**Istruzioni e download:** la pagina del progetto (GitHub Pages, cartella `docs/`) e la sezione *Releases*.

## Come è fatta
- `app/` — l'app Android (Java, nessuna libreria tranne ZXing per il QR).
- `app/src/main/assets/remote.html` — il telecomando sul telefono, servito dalla TV sulla porta 8790.
- `windows/` — l'installatore per Windows (doppio clic, scarica da solo gli strumenti e l'app).
- `installa.sh`, `collega.sh`, `ripristina.sh` — gli stessi passi dal Mac.
- `.github/workflows/build.yml` — a ogni tag `v*` GitHub compila e pubblica `IrbasTV.apk`.

## Privacy
Il telecomando funziona solo sulla rete di casa e solo con il codice di 6 cifre mostrato sulla TV.
I minuti di uso delle app e le domande vanno solo a un server Irbas, se lo colleghi tu.
