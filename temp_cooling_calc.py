import math

T0 = 95.0
T_amb = 22.0
t = 30.0
t_min = t / 60.0
diameter_cm = 7.0
radius_cm = diameter_cm / 2.0
depth_cm = 1.5
volume_ml = math.pi * radius_cm**2 * depth_cm
mass_water_kg = volume_ml / 1000.0

A_top_cm2 = math.pi * radius_cm**2
A_side_cm2 = math.pi * diameter_cm * depth_cm
A_bottom_cm2 = A_top_cm2
A_total_cm2 = A_top_cm2 + A_side_cm2 + A_bottom_cm2
SA_V_ratio = A_total_cm2 / volume_ml

fp_diam = 10.0
fp_depth = 8.0
fp_vol = math.pi * (fp_diam/2)**2 * fp_depth
fp_top = math.pi * (fp_diam/2)**2
fp_side = math.pi * fp_diam * fp_depth
fp_total = fp_top + fp_side + fp_top
fp_sav = fp_total / fp_vol

print("=" * 70)
print("PULSAR GEOMETRY")
print("=" * 70)
print("Volume: %.1f ml, Mass: %.1f g" % (volume_ml, mass_water_kg*1000))
print("Top surface: %.1f cm2" % A_top_cm2)
print("Side walls: %.1f cm2" % A_side_cm2)
print("Bottom: %.1f cm2" % A_bottom_cm2)
print("Total SA: %.1f cm2" % A_total_cm2)
print("SA:V = %.2f /cm" % SA_V_ratio)
print("French Press SA:V = %.2f /cm" % fp_sav)
print("Pulsar/FP ratio = %.1fx" % (SA_V_ratio/fp_sav))
print()

# Convert to SI
A_top_m2 = A_top_cm2 * 1e-4
A_side_m2 = A_side_cm2 * 1e-4
A_bottom_m2 = A_bottom_cm2 * 1e-4
dT = T0 - T_amb
cp_water = 4186

# Channel 1: Evaporative cooling
L_vap = 2270e3
evap_low = 0.00028
evap_high = 0.00056
Q_evap_low = evap_low * A_top_m2 * L_vap
Q_evap_high = evap_high * A_top_m2 * L_vap

# Channel 2: Convective from top
h_conv = 10
Q_conv = h_conv * A_top_m2 * dT

# Channel 3: Through walls
k_plastic = 0.2
wall_t = 0.0025
U_wall = 1 / (wall_t/k_plastic + 1/h_conv)
Q_walls = U_wall * A_side_m2 * dT

# Channel 4: Through bottom (coffee bed)
k_bed = 0.15
bed_d = 0.015
Q_bottom = k_bed * A_bottom_m2 * 20 / bed_d

Q_total_low = Q_evap_low + Q_conv + Q_walls + Q_bottom
Q_total_high = Q_evap_high + Q_conv + Q_walls + Q_bottom

print("=" * 70)
print("HEAT LOSS CHANNELS (Watts)")
print("=" * 70)
print("1. Evaporative (top): %.3f - %.3f W" % (Q_evap_low, Q_evap_high))
print("2. Convective (top):  %.3f W" % Q_conv)
print("3. Wall conduction:   %.3f W" % Q_walls)
print("4. Bottom (bed):      %.3f W" % Q_bottom)
print("TOTAL:                %.3f - %.3f W" % (Q_total_low, Q_total_high))
print()

# Thermal mass
m_grounds = 0.020
cp_grounds = 2500
C_water = mass_water_kg * cp_water
C_grounds = m_grounds * cp_grounds
C_total = C_water + C_grounds

print("=" * 70)
print("THERMAL MASS")
print("=" * 70)
print("Water: %.1f J/K (%.0fg x %d J/kg/K)" % (C_water, mass_water_kg*1000, cp_water))
print("Grounds: %.1f J/K (%.0fg x %d J/kg/K)" % (C_grounds, m_grounds*1000, cp_grounds))
print("Total: %.1f J/K (grounds add %.1f%%)" % (C_total, C_grounds/C_water*100))
print()

drop_low = (Q_total_low * 30) / C_total
drop_high = (Q_total_high * 30) / C_total
drop_mid = (drop_low + drop_high) / 2

print("=" * 70)
print("TEMPERATURE DROP IN 30 SECONDS")
print("=" * 70)
print("Low estimate:  %.2f C" % drop_low)
print("High estimate: %.2f C" % drop_high)
print("Best estimate: %.2f C" % drop_mid)
print()

# Breakdown
print("=" * 70)
print("LOSS CHANNEL BREAKDOWN")
print("=" * 70)
Q_mid = (Q_total_low + Q_total_high) / 2
evap_mid = (Q_evap_low + Q_evap_high) / 2
print("Evaporative:     %.0f%%" % (evap_mid/Q_mid*100))
print("Convective (top): %.0f%%" % (Q_conv/Q_mid*100))
print("Wall conduction:  %.0f%%" % (Q_walls/Q_mid*100))
print("Bottom (bed):     %.0f%%" % (Q_bottom/Q_mid*100))
print()

# Newton law with empirical k
print("=" * 70)
print("NEWTON LAW (empirical k, per minute)")
print("=" * 70)
scenarios = [
    ("Insulated cup k=0.015", 0.015),
    ("Open cup k=0.025", 0.025),
    ("Shallow bowl k=0.045", 0.045),
    ("Pulsar-adjusted k=0.06", 0.06),
    ("Very exposed k=0.08", 0.08),
]
for name, k in scenarios:
    T_t = T_amb + (T0 - T_amb) * math.exp(-k * t_min)
    drop = T0 - T_t
    print("  %s: T(30s)=%.2fC, drop=%.2fC" % (name, T_t, drop))
print()

# Sensitivity
print("=" * 70)
print("SENSITIVITY BY VOLUME (30s and 60s)")
print("=" * 70)
for vol in [50, 75, 100]:
    m = vol / 1000.0
    C = m * cp_water + m_grounds * cp_grounds
    d = vol / (math.pi * radius_cm**2)
    d30 = ((Q_total_low + Q_total_high)/2 * 30) / C
    d60 = ((Q_total_low + Q_total_high)/2 * 60) / C
    print("  %dml (depth %.1fcm): 30s=%.2fC, 60s=%.2fC" % (vol, d, d30, d60))
print()

# Energy to remove for 1C drop
print("=" * 70)
print("ENERGY PERSPECTIVE")
print("=" * 70)
E_1C = C_total
print("Energy to cool slurry 1C: %.1f J" % E_1C)
print("Power lost: %.1f - %.1f W" % (Q_total_low, Q_total_high))
print("Energy removed in 30s: %.1f - %.1f J" % (Q_total_low*30, Q_total_high*30))
print("Degrees removed: %.1f - %.1f C" % (Q_total_low*30/C_total, Q_total_high*30/C_total))
