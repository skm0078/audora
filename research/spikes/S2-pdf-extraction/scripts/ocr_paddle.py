# File: research/spikes/S2-pdf-extraction/scripts/ocr_paddle.py
# Purpose: Minimal PP-OCRv5 mobile pipeline on ONNX Runtime (DB detection + CTC recognition), mirroring what :pdf would run on-device.
import math, time
import cv2
import numpy as np
import onnxruntime as ort

M = "/tmp/s2/models/"
DET_MAX_SIDE = 1920        # long side fed to the detector for an A4 page
DB_THRESH, BOX_THRESH, UNCLIP = 0.3, 0.6, 1.6
REC_H = 48


class PaddleOcr:
    def __init__(self, rec_model, dict_file, threads=4):
        so = ort.SessionOptions(); so.intra_op_num_threads = threads; so.inter_op_num_threads = 1
        self.det = ort.InferenceSession(M + "pp-ocrv5_mobile_det.onnx", so, providers=["CPUExecutionProvider"])
        self.rec = ort.InferenceSession(M + rec_model, so, providers=["CPUExecutionProvider"])
        chars = [l.rstrip("\n") for l in open(M + dict_file, encoding="utf-8")]
        self.chars = ["<blank>"] + chars + [" "]

    def detect(self, img):
        h, w = img.shape[:2]
        r = min(1.0, DET_MAX_SIDE / max(h, w))
        nh, nw = max(32, int(round(h * r / 32)) * 32), max(32, int(round(w * r / 32)) * 32)
        x = cv2.resize(img, (nw, nh)).astype(np.float32) / 255.0
        x = (x - np.array([0.485, 0.456, 0.406], np.float32)) / np.array([0.229, 0.224, 0.225], np.float32)
        x = x.transpose(2, 0, 1)[None]
        prob = self.det.run(None, {self.det.get_inputs()[0].name: x})[0][0, 0]
        mask = (prob > DB_THRESH).astype(np.uint8)
        contours, _ = cv2.findContours(mask, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
        boxes = []
        sx, sy = w / nw, h / nh
        for c in contours:
            if cv2.contourArea(c) < 9:
                continue
            m = np.zeros_like(mask); cv2.drawContours(m, [c], -1, 1, -1)
            if prob[m.astype(bool)].mean() < BOX_THRESH:
                continue
            (cx, cy), (rw, rh), ang = cv2.minAreaRect(c)
            area, peri = rw * rh, 2 * (rw + rh)
            d = area * UNCLIP / peri if peri else 0
            rect = ((cx * sx, cy * sy), ((rw + 2 * d) * sx, (rh + 2 * d) * sy), ang)
            if min(rect[1]) < 6:
                continue
            boxes.append(cv2.boxPoints(rect))
        return boxes

    @staticmethod
    def crop(img, box):
        s = box.sum(1); d = np.diff(box, axis=1).ravel()
        tl, br, tr, bl = box[np.argmin(s)], box[np.argmax(s)], box[np.argmin(d)], box[np.argmax(d)]
        w = int(max(np.linalg.norm(tr - tl), np.linalg.norm(br - bl)))
        h = int(max(np.linalg.norm(bl - tl), np.linalg.norm(br - tr)))
        M_ = cv2.getPerspectiveTransform(np.float32([tl, tr, br, bl]), np.float32([[0, 0], [w, 0], [w, h], [0, h]]))
        out = cv2.warpPerspective(img, M_, (w, h), borderMode=cv2.BORDER_REPLICATE)
        if h > 1.5 * w:
            out = np.rot90(out)
        return out, tl

    def recognize(self, crop):
        h, w = crop.shape[:2]
        nw = max(16, int(math.ceil(REC_H * w / h)))
        x = cv2.resize(crop, (nw, REC_H)).astype(np.float32) / 255.0
        x = ((x - 0.5) / 0.5).transpose(2, 0, 1)[None]
        y = self.rec.run(None, {self.rec.get_inputs()[0].name: x})[0][0]
        idx = y.argmax(1)
        out, prev = [], -1
        for i in idx:
            if i != prev and i != 0 and i < len(self.chars):
                out.append(self.chars[i])
            prev = i
        return "".join(out)

    def page_text(self, img):
        t0 = time.perf_counter()
        boxes = self.detect(img)
        t1 = time.perf_counter()
        items = []
        for b in boxes:
            c, tl = self.crop(img, b)
            ys = b[:, 1]
            items.append((ys.min(), ys.max(), b[:, 0].min(), self.recognize(c)))
        t2 = time.perf_counter()
        # group into lines by vertical overlap, then left-to-right
        items.sort(key=lambda t: (t[0] + t[1]) / 2)
        lines, cur = [], []
        for it in items:
            if cur and (it[0] + it[1]) / 2 > np.mean([c[1] for c in cur]):
                lines.append(cur); cur = []
            cur.append(it)
        if cur:
            lines.append(cur)
        text = "\n".join(" ".join(t[3] for t in sorted(l, key=lambda t: t[2])) for l in lines)
        return text, {"det_ms": (t1 - t0) * 1000, "rec_ms": (t2 - t1) * 1000, "boxes": len(boxes)}
