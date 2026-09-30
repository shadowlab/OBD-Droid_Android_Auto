# Dealer Diagnostic Reports
**OBD-Droid In-House Shop Diagnostics**

Quick diagnostic reports for dealer shop use - diagnose → fix → parts → cost → time.

---

## 📂 Directory Structure

```
dealer-diag-reports/
├── TEMPLATE_diagnostic.md          ← Copy this for new vehicles
├── active/                         ← Currently diagnosing
│   └── Stock_XXX_YYYY_Make_Model.md
├── resolved/                       ← Completed repairs
│   └── Stock_XXX_YYYY_Make_Model.md
├── archived/                       ← Old records (1+ years)
└── screenshots/                    ← OBD-Droid screenshots
    └── Stock_XXX/
        ├── fault_codes.png
        ├── live_data.png
        └── final_clean.png
```

---

## 🚀 Quick Start

**1. Scan vehicle with OBD-Droid:**
```
Main Menu → Fault Codes (Mode 03, 07, 0A)
Main Menu → Live Data (Mode 01)
Main Menu → Emissions (Mode 06)
Tap code → Freeze Frame (Mode 02)
```

**2. Create report:**
```bash
cp TEMPLATE_diagnostic.md active/Stock_001_2017_Nissan_Frontier.md
```

**3. Fill in:**
- VIN (auto-decodes vehicle info)
- Fault codes from OBD scan
- Freeze frame data
- Live data abnormalities
- Diagnosis + repair options
- Parts sources & prices
- Time estimate

**4. Track repair:**
- Update as work progresses
- Screenshot clean scan when done
- Move to `resolved/` when complete

---

## 📱 OBD-Droid Data Collection

**6 Modes to Use:**
1. **Mode 03/07/0A** - Fault codes (confirmed, pending, permanent)
2. **Mode 02** - Freeze frame (parameters when fault occurred)
3. **Mode 01** - Live data (current real-time parameters)
4. **Mode 06** - Emissions (monitor readiness)
5. **Mode 09** - Vehicle info (VIN, calibration IDs)
6. **ECU Scan** - Multi-ECU discovery (advanced)

Each mode documented in template with:
- What it does
- How to use (app navigation)
- What to look for
- Log commands for detailed analysis

---

## 🔧 Repair Approach

**Option 1: Quick Fix** (try first)
- Cheap part replacement ($20-100)
- 15-30 minutes labor
- Test with OBD-Droid
- If works: Done! ✓
- If fails: Go to Option 2

**Option 2: Proper Fix** (if needed)
- Complete repair ($200-500)
- 3-6 hours labor
- Permanent solution
- Verify with OBD-Droid

---

## 💡 Example Report

See `active/2022_GMC_Canyon_P0302_Cylinder2_Misfire.md` for complete example.

**Shows:**
- Full OBD-Droid data collection (all 6 modes)
- Diagnosis correlation (code + freeze + live data)
- Quick fix: MAP sensor swap ($50, 30% success)
- Proper fix: Intake gasket ($470, 95% success)
- Parts from junkyard → Amazon → AutoZone
- Verification steps with OBD-Droid

---

## ✅ Pre-Lot Checklist

**Before selling vehicle, verify with OBD-Droid:**
- [ ] No fault codes (Mode 03/07/0A)
- [ ] MIL (Check Engine) OFF
- [ ] All live data normal (Mode 01)
- [ ] Emissions monitors READY (Mode 06)
- [ ] Test drive 10+ miles
- [ ] Screenshot clean scan

---

---

## 📋 Diagnostic Report Template

# Shop Diagnostic Report
**Stock #[____] - OBD-Droid Scan**

---

## Vehicle Info
*From OBD-Droid VIN Decoder*

| | |
|---|---|
| **Stock #** | |
| **VIN** | [Full VIN] |
| **Year** | [Auto-filled from VIN] |
| **Make** | [Auto-filled from VIN] |
| **Model** | [Auto-filled from VIN] |
| **Trim** | [Auto-filled from VIN] |
| **Engine** | [Size/type - e.g., 4.0L V6] |
| **Displacement** | [Liters/CID] |
| **Fuel Type** | [Gasoline/Diesel/Flex] |
| **Transmission** | [Auto/Manual, speeds] |
| **Drive Type** | [FWD/RWD/AWD/4WD] |
| **Body Style** | [Sedan/Truck/SUV] |
| **Mileage** | [Current odometer] |
| **Acquired From** | [Auction/Trade/Wholesale] |
| **Purchase Price** | $[Amount] |

**OBD Protocol:** [ISO 15765-4 CAN / etc.]
**ECU Module:** [ECM - EngineControl / etc.]

---

## 📱 OBD-Droid Data Collection Guide

### Step 1: Fault Code Scan (Mode 03, 07, 0A)
**App:** *Main Menu → Fault Codes*

**What it does:**
- **Mode 03**: Confirmed DTCs (stored, MIL on)
- **Mode 07**: Pending DTCs (not confirmed yet)
- **Mode 0A**: Permanent DTCs (can't be cleared until fixed)

**How to use:**
1. Tap "Fault Codes" from main menu
2. App automatically scans all 3 modes
3. Screenshot the results
4. Note MIL (Check Engine Light) status

**What to look for:**
- Code count (1-2 codes vs 10+ codes = different issues)
- Code type (P0XXX powertrain, B0XXX body, C0XXX chassis, U0XXX network)
- Permanent codes = must fix before passing emissions

**Log location:** `adb logcat | grep "OBD_SVC_READ_CODES\|PENDINGCODES\|PERMACODES"`

**Record here:**
```
Confirmed (Mode 03):
P0XXX - [Description from app]
P0XXX - [Description from app]

Pending (Mode 07):
P0XXX - [Description]

Permanent (Mode 0A):
P0XXX - [Description]
```

**MIL Status:** ON / OFF
**Screenshot:** `screenshots/Stock_#/fault_codes.png`

---

### Step 2: Freeze Frame Data (Mode 02)
**App:** *Fault Codes → Tap on a code → View Freeze Frame*

**What it does:**
Captures engine parameters at the exact moment the fault code set. This is CRITICAL for diagnosis.

**How to use:**
1. In Fault Codes screen, tap on a DTC
2. Tap "View Freeze Frame" button
3. Screenshot all parameters shown
4. Note which parameter was abnormal

**What to look for:**
- RPM when code set (idle vs highway)
- Speed when code set (0 = idle, 60+ = highway)
- Coolant temp (cold start vs warmed up)
- Load (% - how hard engine working)
- **KEY PARAMETER** that triggered code

**Log location:** `adb logcat | grep "OBD_SVC_FREEZEFRAME\|Frame.*ID"`

**Record here:**
```
Freeze Frame for [DTC]:
Parameter              | Value at Fault | Normal Range  | Status
-----------------------|----------------|---------------|--------
Engine RPM             | [Value]        | 600-750       | ✓ / ⚠️
Vehicle Speed          | [Value] km/h   | Varies        | ✓ / ⚠️
Engine Load            | [Value]%       | 15-25% idle   | ✓ / ⚠️
Coolant Temp           | [Value]°C      | 80-95°C       | ✓ / ⚠️
[Critical parameter]   | [Value]        | [Range]       | ⚠️ ABNORMAL
```

**Screenshot:** `screenshots/Stock_#/freeze_frame_P0XXX.png`

---

### Step 3: Live Data Analysis (Mode 01)
**App:** *Main Menu → Live Data*

**What it does:**
Real-time monitoring of all engine sensors and parameters. Use this to confirm what freeze frame showed.

**How to use:**
1. Tap "Live Data" from main menu
2. Let engine idle for 2 minutes to stabilize
3. Screenshot idle readings
4. Optional: Test drive and screenshot while driving
5. Compare to freeze frame data

**What to look for:**
- Abnormal sensor readings (0.00, maxed out, erratic)
- Fuel trim too high/low (indicates lean/rich)
- Misfire counters (per cylinder)
- Sensor voltages stuck or not switching

**Key Parameters to Monitor:**
```
IDLE READINGS:
Parameter              | Current | Normal Idle | Status
-----------------------|---------|-------------|--------
MAP Sensor             | [Value] | 30-45 kPa   | ✓ / ⚠️
MAF Sensor             | [Value] | 3-6 g/s     | ✓ / ⚠️
Throttle Position      | [Value] | 2-5%        | ✓ / ⚠️
Fuel Trim Bank 1 (ST)  | [Value] | -10 to +10% | ✓ / ⚠️
Fuel Trim Bank 2 (ST)  | [Value] | -10 to +10% | ✓ / ⚠️
Fuel Trim Bank 1 (LT)  | [Value] | -10 to +10% | ✓ / ⚠️
Fuel Trim Bank 2 (LT)  | [Value] | -10 to +10% | ✓ / ⚠️
O2 Sensor B1S1         | [Value] | 0.1-0.9V    | ✓ / ⚠️
O2 Sensor B1S2         | [Value] | 0.6-0.8V    | ✓ / ⚠️
Coolant Temp           | [Value] | 80-95°C     | ✓ / ⚠️
Intake Air Temp        | [Value] | 20-45°C     | ✓ / ⚠️
Engine RPM             | [Value] | 600-750     | ✓ / ⚠️

MISFIRE COUNTERS:
Cylinder 1             | [Count] | 0           | ✓ / ⚠️
Cylinder 2             | [Count] | 0           | ✓ / ⚠️
Cylinder 3             | [Count] | 0           | ✓ / ⚠️
Cylinder 4             | [Count] | 0           | ✓ / ⚠️
[Additional cylinders if V6/V8]
```

**Log location:** `adb logcat | grep "OBD_SVC_DATA\|PID.*0x"`

**Red Flags Found:**
- [Parameter] reading [value] → indicates [problem]
- [Parameter] reading [value] → indicates [problem]

**Screenshots:**
- Idle: `screenshots/Stock_#/live_data_idle.png`
- Driving: `screenshots/Stock_#/live_data_driving.png`

---

### Step 4: Emissions Monitor Status (Mode 06)
**App:** *Main Menu → Emissions*

**What it does:**
Shows readiness of emissions monitors. If monitors are "Not Ready", vehicle may fail inspection OR codes were recently cleared.

**How to use:**
1. Tap "Emissions" from main menu
2. Check "Monitor Readiness" section
3. Note which monitors are incomplete
4. Screenshot the status

**What to look for:**
- All monitors "READY" = Good, can pass inspection
- Some "NOT READY" = May need drive cycle OR repair needed
- Many "NOT READY" after code clear = Suspicious (codes cleared recently?)

**Monitor Status:**
```
Monitor                | Supported | Complete | Notes
-----------------------|-----------|----------|-------
Catalyst               | Yes/No    | ✓ / ✗    |
Heated Catalyst        | Yes/No    | ✓ / ✗    |
EVAP System            | Yes/No    | ✓ / ✗    | Common to be incomplete
O2 Sensor              | Yes/No    | ✓ / ✗    |
O2 Sensor Heater       | Yes/No    | ✓ / ✗    |
EGR System             | Yes/No    | ✓ / ✗    |
Secondary Air          | Yes/No    | ✓ / ✗    |
```

**Ready for Inspection?** YES / NO / NEEDS-DRIVE-CYCLE

**Log location:** `adb logcat | grep "Emissions\|Monitor.*Status"`

**Screenshot:** `screenshots/Stock_#/emissions.png`

**IMPORTANT:**
- If monitors incomplete + codes present = Repair needed first
- If monitors incomplete + no codes = Drive cycle needed
- If all monitors complete + codes = Can diagnose with confidence

---

### Step 5: Vehicle Info (Mode 09)
**App:** *Main Menu → VIN Decoder* (calibration data: *Main Menu → ECU Modules*)

**What it does:**
Retrieves VIN, calibration IDs, and vehicle identification from ECU.

**How to use:**
1. Tap "VIN Decoder" from main menu
2. VIN auto-decodes to Year/Make/Model/Engine
3. Open "ECU Modules" and check the "Calibration ID" and "CVN" fields
4. Screenshot for records

**What to look for:**
- VIN matches title/auction sheet?
- Calibration ID (used for TSB lookup)
- ECU software version

**Log location:** `adb logcat | grep "OBD_SVC_VEH_INFO\|Mode.*09\|VIN"`

**Record here:**
```
VIN: [17 digits from app]
Calibration ID: [From app]
CVN: [Calibration Verification Number]
ECU Name: [From Mode 09]
```

---

### Step 6: ECU Module Scan (Mode 09 Extended)
**App:** *Main Menu → ECU Modules → Scan for ECUs*

**What it does:**
Discovers all ECUs in the vehicle (not just engine). Advanced feature.

**How to use:**
1. Tap "ECU Modules" from main menu
2. Tap "Scan for ECUs"
3. Wait 30-60 seconds for discovery
4. Review list of discovered ECUs
5. Screenshot results

**What to look for:**
- How many ECUs responded? (Should be 3-10+ depending on vehicle)
- Any ECUs missing that should be there?
- Can scan individual ECU fault codes

**Typical ECUs Found:**
```
ECU Address | Name                    | Calibration ID
------------|-------------------------|----------------
0x7E8       | ECM - Engine Control    | [ID from scan]
0x7E9       | TCM - Transmission      | [ID from scan]
0x7EA       | ABS/VDC                 | [ID from scan]
0x7EB       | [Other module]          | [ID from scan]
```

**Log location:** `adb logcat | grep "ECU.*Discovery\|Mode.*09.*0A"`

**Screenshot:** `screenshots/Stock_#/ecu_modules.png`

---

## 🔍 Diagnosis Using OBD Data

### Interpreting the Data

**1. Start with Fault Codes:**
- What system is affected? (P0 = powertrain, P1 = manufacturer)
- Generic or specific? (P0300 = random misfire, P0302 = cylinder 2)

**2. Check Freeze Frame:**
- When did it happen? (Cold start? Highway speed?)
- What was abnormal at that moment?

**3. Confirm with Live Data:**
- Is the problem still present NOW?
- Does live data match freeze frame issue?

**4. Cross-reference Emissions:**
- Are related monitors failing?
- Were codes recently cleared?

**Example Diagnosis Flow:**
```
Code P0302 (Cylinder 2 Misfire)
     ↓
Freeze Frame shows: MAP Sensor B = 0.00 kPa
     ↓
Live Data confirms: MAP Sensor B still 0.00 kPa
                    Fuel Trim Bank 2 = +12.5% (lean)
                    Misfire Counter Cyl 2 = 47 counts
     ↓
Emissions: O2 Sensor monitor incomplete
     ↓
DIAGNOSIS: Vacuum leak on Bank 2 causing lean condition
           and misfire on Cylinder 2
     ↓
ROOT CAUSE: MAP Sensor B failure OR intake manifold gasket leak
```

---

## 🔧 What's Wrong & How to Fix It

### Issue #1: [Problem Name]

**Fault Code:** [P0XXX]
**Severity:** 🔴 CRITICAL / 🟠 HIGH / 🟡 MEDIUM / 🟢 MINOR

**Evidence from OBD-Droid:**
- **Mode 03**: [DTC code]
- **Mode 02 (Freeze Frame)**: [Critical parameter] = [Abnormal value]
- **Mode 01 (Live Data)**: [Parameter] currently = [Value]
- **Mode 06 (Emissions)**: [Monitor] incomplete/failed

**Diagnosis:**
Based on OBD data correlation: [What's actually wrong]

---

### 🛠️ Repair Option 1: Quick Fix

**Try:** [Simple part replacement]
**Why:** [Reasoning based on OBD data]

**Parts:**
| Part | Source | Price | Stock |
|------|--------|-------|-------|
| [Part] | Junkyard | $20-30 | Call ahead |
| [Part] | Amazon | $35-50 | 2-day Prime |
| [Part] | AutoZone | $65-85 | Same day |

**Labor:** 0.3 hrs
**Difficulty:** ⭐☆☆☆☆

**How to Test Fix:**
1. Install part
2. Clear codes: *OBD-Droid → Fault Codes → Menu → Clear*
3. Monitor live data: *Live Data → Watch [parameter]*
4. Test drive 10 miles
5. Re-scan: *Fault Codes*

**Verify with OBD-Droid:**
- **FIXED:** [Parameter] now reads [normal value], no codes ✓
- **NOT FIXED:** [Parameter] still abnormal → Try Option 2

**Success Rate:** [X]%
**Total Cost:** $50-115
**Time:** Same day

---

### 🔨 Repair Option 2: Proper Fix

**Do:** [Complete repair]
**Why:** [If quick fix failed, this is the real issue]

**Parts:**
| Part | Source | Price | Notes |
|------|--------|-------|-------|
| [Part 1] | Amazon | $50 | Budget |
| [Part 1] | AutoZone | $120 | OEM quality |
| [Part 2] | AutoZone | $35 | Required |
| [Part 3] | AutoZone | $20 | Consumable |

**Total Parts:** $105 (budget) or $175 (OEM)
**Labor:** 5 hrs
**Difficulty:** ⭐⭐⭐☆☆

**Special Tools:**
- [Tool 1]
- [Tool 2]

**Verify with OBD-Droid After Repair:**
1. Clear codes: *Fault Codes → Clear*
2. Check live data: *Live Data*
   - [Parameter 1]: [Normal value] ✓
   - [Parameter 2]: [Normal value] ✓
   - Misfire counters: 0 ✓
3. Test drive 20+ miles (highway + city)
4. Re-scan: *Fault Codes*
   - No codes returned ✓
5. Check emissions: *Emissions*
   - Monitors completing ✓

**Success Rate:** 95%+
**Total Cost:** $405-475
**Time:** 2-3 days

---

## 💰 Cost Summary

**Best Case (Quick fix works):**
- Parts: $[Amount]
- Labor: [Hours] hrs
- **Total: $[Amount]**
- **Time: [Days]**

**Worst Case (Need full repair):**
- Parts: $[Amount]
- Labor: [Hours] hrs
- **Total: $[Amount]**
- **Time: [Days]**

---

## ⏱️ Timeline

- [x] Initial OBD-Droid scan (all modes)
- [ ] Order parts - ETA: [Date]
- [ ] Install parts - [Hours] hrs
- [ ] Test drive + OBD verification
- [ ] Final clean scan
- [ ] Ready for lot

**Target completion:** [Date]

---

## ✅ Pre-Lot Final Check

**Use OBD-Droid to verify BEFORE putting on lot:**

- [ ] **Mode 03/07/0A**: No fault codes ✓
- [ ] **MIL Status**: OFF ✓
- [ ] **Mode 01**: All live data parameters normal ✓
- [ ] **Mode 02**: No freeze frames stored ✓
- [ ] **Mode 06**: Emissions monitors READY ✓
- [ ] **Test drive**: 20+ miles, no issues ✓

**Final Screenshots:**
```
adb shell screencap -p /sdcard/final_scan.png
adb pull /sdcard/final_scan.png screenshots/Stock_#/final_clean.png
```

**Final Scan Results:**
```
Date: [Date]
Fault Codes: NONE ✓
MIL: OFF ✓
Monitors: ALL READY ✓
Live Data: NORMAL ✓
```

---

## 📝 Shop Notes

**Tech:** [Name]
**Scan Date:** [Date]

**Day-by-Day Log:**
```
[Date] - OBD scan completed (all modes)
       - Found codes: [List]
       - Freeze frame captured
       - Live data shows: [Abnormalities]
       - Diagnosis: [Problem]

[Date] - Ordered [parts] from [source], ETA [date]

[Date] - Parts arrived, started repair

[Date] - Repair completed
       - Cleared codes via OBD-Droid
       - Live data now normal
       - Test drive OK

[Date] - Final verification scan
       - No codes ✓
       - All monitors ready ✓
       - Ready for lot ✓
```

**Logs for Reference:**
```bash
# Pull complete OBD logs if needed
adb logcat -d | grep -E "ObdProt|Mode.*0[0-9]|OBD_SVC" > logs/Stock_#_obd_session.log
```

---

**Report Created:** [Date]
**Last Updated:** [Date]
**Status:** DIAGNOSING / ORDERING-PARTS / IN-REPAIR / READY

---

**Last Updated:** October 2025
