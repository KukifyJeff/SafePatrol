import os
import pandas as pd

# CSV 文件目录（相对于本脚本文件）
CSV_DIR = os.path.join(os.path.dirname(__file__), "csv")

# 要检查的四个初始 CSV 文件名（不带扩展名）
REQUIRED_FILES = [
    "points",             # points.csv
    "equipments",         # equipments.csv
    "check_items",        # check_items.csv
    "equipment_status"    # equipment_status.csv
]

# 辅助：读取 CSV
def load_csv(name):
    path = os.path.join(CSV_DIR, f"{name}.csv")
    if not os.path.exists(path):
        print(f"❌ 缺少文件: {path}")
        return pd.DataFrame()
    try:
        df = pd.read_csv(path, dtype=str)
    except Exception as e:
        print(f"❌ 读取 {path} 失败: {e}")
        return pd.DataFrame()
    print(f"✅ 已读取 {name}.csv, 行数: {len(df)}")
    return df

# 读取四个文件
points = load_csv("points")
equipments = load_csv("equipments")
check_items = load_csv("check_items")
equipment_status = load_csv("equipment_status")

print("\n=== 开始外键完整性检查（基于 points / equipments / check_items / equipment_status） ===\n")

missing_reports = []

# 1) equipments -> points: equipment.pointId 必须在 points.pointId 中
if not equipments.empty:
    if "pointId" not in equipments.columns:
        print("❌ equipments.csv 缺少列 'pointId'。")
    elif not points.empty:
        invalid_equipment_points = equipments[~equipments["pointId"].isin(points["pointId"])]["pointId"].unique().tolist()
        if invalid_equipment_points:
            missing_reports.append(("equipments.pointId", invalid_equipment_points))

# 2) check_items -> equipments: check_items.equipmentId 必须在 equipments.equipmentId 中
if not check_items.empty:
    if "equipmentId" not in check_items.columns:
        print("❌ check_items.csv 缺少列 'equipmentId'。")
    else:
        # 检查空 equipmentId
        empty_equip_rows = check_items[check_items["equipmentId"].isna() | (check_items["equipmentId"].str.strip() == "")]
        if not empty_equip_rows.empty:
            for idx, row in empty_equip_rows.iterrows():
                item_name = row.get("itemName", "（未知项）")
                print(f"❌ check_items.csv 第 {idx + 2} 行 equipmentId 为空（itemName={item_name}）")
        if not equipments.empty:
            invalid_check_equips = check_items[~check_items["equipmentId"].isin(equipments["equipmentId"])]["equipmentId"].unique().tolist()
            if invalid_check_equips:
                missing_reports.append(("check_items.equipmentId", invalid_check_equips))

# 3) equipment_status -> equipments: equipment_status.equipmentId 必须在 equipments.equipmentId 中
if not equipment_status.empty:
    if "equipmentId" not in equipment_status.columns:
        print("❌ equipment_status.csv 缺少列 'equipmentId'。")
    elif not equipments.empty:
        invalid_status_equips = equipment_status[~equipment_status["equipmentId"].isin(equipments["equipmentId"])]["equipmentId"].unique().tolist()
        if invalid_status_equips:
            missing_reports.append(("equipment_status.equipmentId", invalid_status_equips))

# 4) （可选）检查重复 ID
# 检查 points, equipments, check_items 中的主键重复
if not points.empty and "pointId" in points.columns:
    dup = points[points.duplicated(subset=["pointId"], keep=False)]["pointId"].unique().tolist()
    if dup:
        missing_reports.append(("points.duplicate_pointId", dup))

if not equipments.empty and "equipmentId" in equipments.columns:
    dup = equipments[equipments.duplicated(subset=["equipmentId"], keep=False)]["equipmentId"].unique().tolist()
    if dup:
        missing_reports.append(("equipments.duplicate_equipmentId", dup))

if not check_items.empty and "itemId" in check_items.columns:
    dup = check_items[check_items.duplicated(subset=["itemId"], keep=False)]["itemId"].unique().tolist()
    if dup:
        missing_reports.append(("check_items.duplicate_itemId", dup))

# 输出报告
if missing_reports:
    print("❌ 外键或数据完整性检查发现以下问题：\n")
    for field, vals in missing_reports:
        print(f"  - {field} 缺失/异常 {len(vals)} 项：{vals[:20]}{' ...' if len(vals) > 20 else ''}")
    print("\n请根据以上提示修复 CSV 后重试。")
else:
    print("✅ 所有检查通过（points / equipments / check_items / equipment_status）。")

print("\n=== 检查结束 ===")