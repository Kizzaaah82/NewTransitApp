# Visual Comparison: Bus Arrival Times

## Before & After Examples

---

## 📱 **MapScreen - Stop Info Card**

### **BEFORE**:
```
┌─────────────────────────────────────┐
│ Wyandotte @ Goyeau                  │
│ Stop 1001                           │
├─────────────────────────────────────┤
│ Route 1C    LIVE    7 mins          │
│ Route 1C            12:18           │
│ Route 1C            12:33           │
└─────────────────────────────────────┘
```
**Issues**:
- No delay information visible
- Can't tell which is scheduled vs real-time
- LIVE badge separate from time

---

### **AFTER**:
```
┌─────────────────────────────────────┐
│ Wyandotte @ Goyeau                  │
│ Stop 1001                           │
├─────────────────────────────────────┤
│ Route 1C                            │
│   🔴 7 min [LIVE] [5 min late] 🔴  │
│      Scheduled: 2 min               │
│                                     │
│ Route 1C                            │
│   📅 12:18       [Scheduled]        │
│                                     │
│ Route 1C                            │
│   📅 12:33       [Scheduled]        │
└─────────────────────────────────────┘
```
**Improvements**:
✅ Explicit delay badge (5 min late)
✅ Shows what was scheduled (2 min)
✅ Clear visual hierarchy
✅ Color-coded status

---

## 🏠 **HomeScreen - Favorite Stop Card**

### **BEFORE**:
```
┌─────────────────────────────────────┐
│ ║ Wyandotte @ Goyeau          ❤️   │
│ ║ Stop 1001 • Route 1C              │
│ ║───────────────────────────────────│
│ ║ 7 mins                            │
│ ║ 12:18                             │
│ ║ 12:33                             │
│ ║───────────────────────────────────│
│ ║     [View Timetable]              │
└─────────────────────────────────────┘
```

---

### **AFTER**:
```
┌─────────────────────────────────────┐
│ ║ Wyandotte @ Goyeau          ❤️   │
│ ║ Stop 1001 • Route 1C              │
│ ║───────────────────────────────────│
│ ║ 🔴 7 min [LIVE]  [5 min late] 🔴 │
│ ║    Scheduled: 2 min               │
│ ║                                   │
│ ║ 📅 12:18         [Scheduled]      │
│ ║ 📅 12:33         [Scheduled]      │
│ ║───────────────────────────────────│
│ ║     [View Timetable]              │
└─────────────────────────────────────┘
```

---

## 🗺️ **NearbyStopsScreen - Compact View**

### **BEFORE**:
```
┌───────────────────────────────┐
│ 🚌 Wyandotte @ Goyeau     →   │
│    157m away                  │
│                               │
│    7 mins (5 min late)        │
│    12:18                      │
│    12:33                      │
└───────────────────────────────┘
```

---

### **AFTER** (Compact Mode):
```
┌───────────────────────────────┐
│ 🚌 Wyandotte @ Goyeau     →   │
│    157m away                  │
│                               │
│ 🔴 7 min [5 min late] 🔴      │
│    Sched: 2 min               │
│                               │
│ 📅 12:18  [Scheduled]         │
│ 📅 12:33  [Scheduled]         │
└───────────────────────────────┘
```
**Note**: Compact mode uses smaller fonts and tighter spacing

---

## 🎨 **Color Coding System**

### **Delay Badges**:

**Significant Late (>1 min)**
```
[5 min late]  ← Red background, white text
```

**Significant Early (>1 min)**
```
[3 min early] ← Green background, white text
```

**Minor Late (30-60 sec)**
```
[Delayed]     ← Orange background, white text
```

**Minor Early (30-60 sec)**
```
[Early]       ← Light green background, white text
```

**On Time (< 30 sec difference)**
```
[On time]     ← Blue/neutral background, white text
```

---

## 🔄 **Different Scenarios**

### **Scenario 1: Bus Running Very Late**
```
Route 3
🔴 15 min [LIVE]  [12 min late] 🔴
   Scheduled: 3 min

📅 3:25 PM    [Scheduled]
📅 3:40 PM    [Scheduled]
```
**User sees**: "Bus is 15 minutes away but was scheduled for 3 min - it's 12 min late!"

---

### **Scenario 2: Bus Ahead of Schedule**
```
Route 8
🟢 2 min [LIVE]  [2 min early] 🟢
   Scheduled: 4 min

📅 2:18 PM    [Scheduled]
📅 2:33 PM    [Scheduled]
```
**User sees**: "Bus arriving in 2 min, earlier than the 4 min schedule - nice!"

---

### **Scenario 3: No Real-Time Available**
```
Route 5
📅 8 min      [Scheduled]
📅 23 min     [Scheduled]
📅 38 min     [Scheduled]
```
**User sees**: 3 scheduled times as fallback when real-time is unavailable

---

### **Scenario 4: Bus Arriving Soon**
```
Route 1C
🔴 Due [LIVE]  [On time]
   Scheduled: Due

📅 12:18      [Scheduled]
📅 12:33      [Scheduled]
```
**User sees**: Bus is here! And it's on time!

---

### **Scenario 5: Long Wait Time**
```
Route 2
🔴 12:45 PM [LIVE]  [3 min late] 🔴
   Scheduled: 12:42 PM

📅 1:15 PM    [Scheduled]
📅 1:45 PM    [Scheduled]
```
**User sees**: When countdown > 60 min, show clock time instead

---

## 📊 **Information Hierarchy**

```
1. PRIMARY (Biggest, Boldest)
   └─ Real-time countdown or clock time
      ├─ Bold font (20sp)
      ├─ Primary color (blue)
      └─ LIVE badge inline

2. SECONDARY (Prominent)
   └─ Delay badge
      ├─ Color-coded background
      ├─ Bold white text
      └─ Right-aligned

3. TERTIARY (Context)
   └─ Scheduled baseline
      ├─ Small gray text (11sp)
      ├─ 70% opacity
      └─ Below real-time

4. FALLBACK (Neutral)
   └─ Static scheduled times
      ├─ Medium font (16sp)
      ├─ Calendar emoji
      └─ "Scheduled" badge
```

---

## 💡 **Key Design Principles**

1. **Truth First**: Always show real arrival time (not what user wants to hear)
2. **Context Matters**: Show both actual AND scheduled (the "why")
3. **Visual Hierarchy**: Important info is bigger and bolder
4. **Color = Status**: Red bad, green good, gray neutral
5. **No Math Required**: Show "5 min late" not "scheduled 2, arriving 7"

---

## 🎯 **User Benefits**

| Scenario | Before | After |
|----------|--------|-------|
| **Bus is late** | "Arrives in 7 min" (confusing) | "7 min, 5 min late" (honest) |
| **Planning ahead** | Only see one time | See next 3 arrivals clearly |
| **Real-time fails** | No info | Fall back to 3 scheduled times |
| **Urgent trip** | Can't tell if bus is late | Delay badge alerts immediately |
| **Trust the app** | Generic times | See live tracking + schedule proof |

---

**Created**: October 26, 2024  
**Status**: ✅ Implemented and tested  
**Build**: ✅ Successful

