# -*- coding: utf-8 -*-
"""
从教务系统导出的《课表打印.xls》生成 timetable.json（StudyReminder 配置）。
用法:
  python tools/gen_timetable.py "课表打印.xls" [第1周的周一, 默认 2026-08-31] \
    > app/src/main/assets/config/timetable.json
换学期: 重新导出 xls → 传入新学期第 1 周的周一 → 覆盖 timetable.json 即可。
"""
import json
import re
import sys
import datetime as dt

import xlrd

SHORT_NAMES = {
    "毛泽东思想和中国特色社会主义理论体系概论": "毛概",
    "基于python语言的工程应用系统开发": "Python应用开发",
}
SLOT_LABELS = ["第1-2节", "第3-4节", "第5-6节", "第7-8节", "第9-10节", "第11-12节"]
DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]


def parse_cell(text):
    """一个格子 → [(周次expr, 课程名, 教室), ...]"""
    entries = []
    # 预处理：新版教务导出会把下一门课的「课名[教师]」挤在上一条的教室后面，
    # 在"空格+课名[教师]"边界插入换行，统一成一行一条
    text = re.sub(r" +(?=[^\s;\[\]]+\[[^\]]+\])", "\n", text)
    lines = [l.strip() for l in text.split("\n") if l.strip()]
    name = None
    for line in lines:
        m = re.match(r"^(.*?)\[(.+?)\]\s*$", line)
        if m and not re.match(r"^\d", line):
            name = m.group(1).strip()
            continue
        if "周;" in line and name and re.match(r"^\d", line):
            weeks_part = line.split(";", 1)[0].strip().rstrip("周")
            rest = line.split(";", 1)[1] if ";" in line else ""
            # 教室后跟课程代码（如 " B04209800"）时剥离；无教室时 rest 直接以课程代码开头
            room = re.split(r"\s+[A-Z][A-Z0-9]{7,}\s", " " + rest + " ", maxsplit=1)[0].strip()
            if re.match(r"^[A-Z][A-Z0-9]{7,}\b", rest):
                room = ""
            name = SHORT_NAMES.get(name, name)
            entries.append((weeks_part, name, room))
            name = None
    return entries


def in_week(expr, wk):
    for part in expr.split(","):
        if "-" in part:
            a, b = part.split("-")
            if int(a) <= wk <= int(b):
                return True
        elif part and int(part) == wk:
            return True
    return False


def main():
    path = sys.argv[1]
    start = sys.argv[2] if len(sys.argv) > 2 else "2026-08-31"
    wb = xlrd.open_workbook(path)
    sh = wb.sheet_by_index(0)

    grid = {}
    for r in range(3, min(sh.nrows, 9)):
        slot = r - 3
        for c in range(1, min(sh.ncols, 8)):
            v = sh.cell_value(r, c)
            if not v:
                continue
            for weeks, name, room in parse_cell(str(v)):
                grid.setdefault((c, slot), []).append((weeks, name, room))

    out = {"semesterStart": start, "slotLabels": SLOT_LABELS, "entries": {}}
    for day in range(1, 8):
        day_entries = sorted(
            [(slot, e) for (dd, slot), es in grid.items() if dd == day for e in es],
            key=lambda x: x[0],
        )
        if day_entries:
            out["entries"][DAYS[day - 1]] = [
                {"slot": s, "name": n, "room": r, "weeks": w} for s, (w, n, r) in day_entries
            ]

    print(json.dumps(out, ensure_ascii=False, indent=1))

    # 自检输出到 stderr（不影响 JSON 输出）
    y, m, d = (int(x) for x in start.split("-"))
    today = dt.date.today()
    kw = (today - dt.date(y, m, d)).days // 7 + 1
    print(f"// 自检: 今天 {today} 是第 {kw} 教学周 {DAYS[today.weekday()]}", file=sys.stderr)
    shown = 0
    for (dd, slot), es in sorted(grid.items()):
        if dd != today.isoweekday():
            continue
        for weeks, name, room in es:
            if in_week(weeks, kw):
                print(f"//   本周有课: {SLOT_LABELS[slot]} {name} {room} [{weeks}周]", file=sys.stderr)
            shown += 1
    if shown == 0:
        print("//   今天课表无安排", file=sys.stderr)


if __name__ == "__main__":
    main()
