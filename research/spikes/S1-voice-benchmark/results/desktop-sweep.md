# S1 speaker sweep (desktop, x86, 2 threads)

Kitten v0.8 nano and Supertonic 3 speakers on the same texts. RTF excludes the first sentence; CER over prose + formal; texts the ASR answered in Urdu script are not scored. Audio = total seconds for the four texts (pace). Raw data: desktop-sweep.json.

| Voice | Speaker | Load s | First render s | RTF | CER | Not scored | Audio s |
|---|---|---|---|---|---|---|---|
| kitten-en-nano | 0 | 1.00 | 1.51 | 0.326 | 0.0 % | 0 | 59.2 |
| kitten-en-nano | 1 | 0.77 | 2.01 | 0.328 | 0.0 % | 0 | 84.3 |
| kitten-en-nano | 2 | 0.70 | 1.55 | 0.316 | 0.0 % | 0 | 61.6 |
| kitten-en-nano | 3 | 0.76 | 2.59 | 0.326 | 0.0 % | 0 | 63.1 |
| kitten-en-nano | 4 | 0.77 | 1.57 | 0.314 | 0.0 % | 0 | 54.2 |
| kitten-en-nano | 5 | 0.72 | 1.77 | 0.337 | 0.0 % | 0 | 62.4 |
| kitten-en-nano | 6 | 0.73 | 1.75 | 0.307 | 0.0 % | 0 | 78.7 |
| kitten-en-nano | 7 | 0.58 | 1.71 | 0.340 | 0.0 % | 0 | 55.9 |
| supertonic-en | 0 | 0.73 | 0.79 | 0.163 | 0.0 % | 0 | 57.4 |
| supertonic-en | 1 | 0.62 | 0.82 | 0.169 | 0.0 % | 0 | 54.1 |
| supertonic-en | 2 | 0.77 | 1.02 | 0.163 | 0.0 % | 0 | 59.0 |
| supertonic-en | 3 | 0.62 | 0.83 | 0.177 | 0.2 % | 0 | 49.5 |
| supertonic-en | 4 | 0.67 | 0.86 | 0.173 | 0.0 % | 0 | 55.7 |
| supertonic-en | 5 | 0.65 | 0.83 | 0.168 | 0.0 % | 0 | 57.5 |
| supertonic-en | 6 | 0.71 | 0.90 | 0.160 | 0.0 % | 0 | 56.3 |
| supertonic-en | 7 | 0.75 | 0.85 | 0.176 | 0.0 % | 0 | 47.6 |
| supertonic-en | 8 | 0.71 | 0.80 | 0.183 | 0.0 % | 0 | 54.0 |
| supertonic-en | 9 | 0.87 | 0.94 | 0.179 | 0.0 % | 0 | 57.3 |
| supertonic-hi | 0 | 0.62 | 1.07 | 0.168 | 8.3 % | 1 | 53.7 |
| supertonic-hi | 1 | 0.65 | 0.83 | 0.174 | 2.3 % | 1 | 50.6 |
| supertonic-hi | 2 | 0.75 | 1.17 | 0.169 | 2.4 % | 0 | 55.2 |
| supertonic-hi | 3 | 0.71 | 0.91 | 0.184 | 6.5 % | 2 | 46.3 |
| supertonic-hi | 4 | 0.92 | 0.88 | 0.175 | 3.9 % | 1 | 52.1 |
| supertonic-hi | 5 | 0.64 | 0.91 | 0.172 | 3.5 % | 1 | 53.8 |
| supertonic-hi | 6 | 0.72 | 0.93 | 0.171 | 3.6 % | 0 | 52.7 |
| supertonic-hi | 7 | 0.63 | 0.77 | 0.175 | 6.8 % | 1 | 44.4 |
| supertonic-hi | 8 | 0.76 | 0.82 | 0.166 | 4.5 % | 0 | 50.5 |
| supertonic-hi | 9 | 0.58 | 1.02 | 0.167 | 6.2 % | 0 | 53.7 |

Chosen for the phone run: Kitten speaker 7 (expr-voice-5-f; speaker 1 paces about 50 % slower than the other voices, which would bias the blind test), Supertonic Hindi speaker 2 (lowest CER with every text scored), Supertonic English speaker 0 (all equivalent).
