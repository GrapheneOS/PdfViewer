# TeX Files for PDF Testing

The LaTeX files are used to generate test PDFs.

---

## 1. `test-simple.tex`

```tex
\documentclass{article}
\usepackage[pdftitle={Test Document}, pdfauthor={Test Author}]{hyperref}
\pagestyle{empty}
\begin{document}
	Test Text
\end{document}
```

---

## 2. `test-multipage.tex`

```tex
\documentclass{article}
\usepackage[pdftitle={Multi Page Document}, pdfauthor={Test Author}]{hyperref}
\pagestyle{empty}
\begin{document}
	
	\section{Chapter One}
	Page One Content
	\newpage
	
	\section{Chapter Two}
	Page Two Content
	\newpage
	
	\section{Chapter Three}
	Page Three Content
	\newpage
	
	\section{Chapter Four}
	Page Four Content
\end{document}
```

---

## 3. `test-encrypted.tex`

```tex
\documentclass{article}
\usepackage[pdftitle={Encrypted Document}, pdfauthor={Test Author}]{hyperref}
\pagestyle{empty}
\begin{document}
	Password-Protected Content
\end{document}
```

**Encrypt:**
```bash
qpdf --encrypt testpass testpass 256 -- test-encrypted.pdf test-encrypted.pdf
```

---

## 4. `test-large.pdf`

This 73-page document is generated entirely from `test-simple.pdf`. It avoids
depending on or redistributing a third-party document while providing enough
pages to exercise progressive layout, late-page navigation, and lazy rendering.

```bash
inputs=()
for _ in {1..73}; do
    inputs+=(test-simple.pdf)
done
pdfunite "${inputs[@]}" test-large.pdf
```
