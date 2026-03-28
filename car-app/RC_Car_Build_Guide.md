# 4WD Bluetooth RC Car — Complete Build Guide
> ESP32 + L298N Motor Driver + LiPo Battery

---

## Table of Contents
1. [Parts List](#parts-list)
2. [Wiring](#wiring)
3. [Arduino IDE Setup](#arduino-ide-setup)
4. [ESP32 Code](#esp32-code)
5. [Command Reference](#command-reference)
6. [Bluetooth JSON Protocol](#bluetooth-json-protocol)
7. [LiPo Battery Guide](#lipo-battery-guide)
8. [Troubleshooting](#troubleshooting)
9. [Tips & Notes](#tips--notes)

---

## Parts List

### Mechanical

| Component | Specification | Approx. Cost (INR) |
|---|---|---|
| 4WD Chassis Kit | Robocraze Double Layer Longer Version — includes 4x DC motors, wheels, chassis, wires | ₹534 |

### Electronics

| Component | Specification | Approx. Cost (INR) |
|---|---|---|
| ESP32 DevKit | SquadPixel ESP-WROOM-32, 38-pin, Micro USB, dual core, WiFi + Bluetooth | ₹350–450 |
| L298N Motor Driver | Electronic Spices L298 Motor Driver Board | ₹150–200 |
| Jumper Wires | Electronic Spices 60-piece set (M-M, M-F, F-F) 20cm | ₹50–100 |

### Power

| Component | Specification | Approx. Cost (INR) |
|---|---|---|
| LiPo Battery | MatLogix 11.1V 3S 2200mAh 80C, XT60 female connector | ₹600–900 |
| LiPo Charger | Robocraze B3AC Compact Charger 7.4–11.1V | ₹400–500 |
| XT60 Adapter | 2x XT60 Male to HXT 4mm Banana, 12 gauge wire | ₹50–100 |
| LiPo Safe Bag | REES52 Silver LiPo Guard Sleeve | ₹200 |
| Voltage Checker | Abhith India 1-8S LiPo Voltage Tester/Buzzer Alarm | ₹100 |
| Power Bank | Any 10000mAh USB power bank (powers ESP32) | existing |

---

## Wiring

### Overview

The car uses **two separate power sources:**
- LiPo battery (11.1V) → L298N → powers the motors
- USB Power Bank → ESP32 (via Micro USB) → powers the brain

> ⚠️ **SAFETY RULE:** Always disconnect the LiPo battery BEFORE touching or changing any wires. Never swap wires while power is connected — this can permanently damage the L298N.

---

### Battery → L298N

Connect using the XT60 Male adapter:
- **Red wire** → `12V` screw terminal on L298N
- **Black wire** → `GND` screw terminal on L298N

> 💡 The 12 gauge wire from the XT60 adapter may be too thick to fit inside the screw terminal hole. Wrap the wire around the screw itself and tighten firmly instead. Make sure bare copper does not touch adjacent terminals.

---

### Motors → L298N

Each motor has two wires. Connect to L298N output screw terminals:

| L298N Terminal | Motors connected |
|---|---|
| OUT1 + OUT2 | Both left-side motors (front-left and rear-left) — double up wires in terminals |
| OUT3 + OUT4 | Both right-side motors (front-right and rear-right) — double up wires in terminals |

> 💡 It doesn't matter which motor wire goes in OUT1 vs OUT2. If a wheel spins the wrong direction, just swap those two wires. Always disconnect power first!

---

### ESP32 → L298N

Use **Female-to-Female (F-F) jumper wires**. Clip one end on ESP32 pin, other end on L298N pin:

| L298N Pin | ESP32 Pin (board label) | Purpose |
|---|---|---|
| IN1 | D26 | Left motors direction |
| IN2 | D27 | Left motors direction |
| IN3 | D14 | Right motors direction |
| IN4 | D12 | Right motors direction |
| ENA (signal pin) | D25 | Left motors speed (PWM) |
| ENB (signal pin) | D33 | Right motors speed (PWM) |
| GND | GND | Common ground — REQUIRED |

> ⚠️ Do NOT connect L298N 5V to ESP32 VIN. The ESP32 is powered separately by a USB power bank.

---

### ENA and ENB Jumper Caps

ENA and ENB each have 2 pins covered by a small plastic jumper cap. Remove caps for PWM speed control:

1. Remove the jumper cap from ENA
2. Remove the jumper cap from ENB
3. Connect a wire to the **signal pin** (away from motor terminals) on each
4. Connect ENA signal pin → D25, ENB signal pin → D33

> 💡 If the wrong pin is connected, motors won't respond to speed changes. Swap to the other pin if needed.

---

### Power-on Order

Always follow this order:
1. Connect all wires first
2. Connect USB power bank to ESP32
3. Connect LiPo battery to L298N **last**

---

## Arduino IDE Setup

### Install Arduino IDE
1. Download from https://www.arduino.cc/en/software
2. Install normally for your OS

### Add ESP32 Board Support
1. Open Arduino IDE
2. Go to **File → Preferences** (Windows) or **Arduino IDE → Preferences** (Mac), or press `Ctrl+,` / `Cmd+,`
3. Find "Additional boards manager URLs" and paste:
```
https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json
```
4. Click OK
5. Go to **Tools → Board → Boards Manager**
6. Search `ESP32` and install **"esp32 by Espressif Systems"**

### Select Board and Port
1. Go to **Tools → Board → ESP32 Arduino → ESP32 Dev Module**
2. Connect ESP32 to computer via Micro USB
3. Go to **Tools → Port** and select the ESP32 port

> 💡 On Mac, if no port appears, install the CH340 driver from https://www.wch-ic.com/downloads/CH341SER_MAC_ZIP.html. After install and Mac restart, the port `/dev/tty.SLAB_USBtoUART` should appear.

### Upload Code
1. Paste the code (see below) into Arduino IDE
2. Click the **Upload button** (right arrow icon)
3. When `Connecting....` appears at the bottom, press and hold the **BOOT** button on ESP32
4. Hold until upload progress bar starts moving
5. Wait for **"Done uploading"**

---

## ESP32 Code

```cpp
#include "BluetoothSerial.h"

BluetoothSerial SerialBT;

// Motor control pins
#define IN1 26
#define IN2 27
#define IN3 14
#define IN4 12
#define ENA 25
#define ENB 33

// Configurable values
int speed = 75;          // Drive speed (0-255)
int turnSpeed = 100;     // Turn speed (0-255)
int increment = 10;      // Speed change step
int turnIncrement = 10;  // Turn speed change step
String currentState = "STOP";

void setup() {
  pinMode(IN1, OUTPUT);
  pinMode(IN2, OUTPUT);
  pinMode(IN3, OUTPUT);
  pinMode(IN4, OUTPUT);
  pinMode(ENA, OUTPUT);
  pinMode(ENB, OUTPUT);

  SerialBT.begin("RoboCar");
  Serial.begin(115200);
  stopCar();
  Serial.println("Ready!");
}

void loop() {
  if (SerialBT.available()) {
    char cmd = SerialBT.read();
    Serial.println(cmd);
    if (cmd == 'F') forward();
    else if (cmd == 'B') backward();
    else if (cmd == 'L') turnLeft();
    else if (cmd == 'R') turnRight();
    else if (cmd == 'S') stopCar();
    else if (cmd == '+') speedUp();
    else if (cmd == '-') speedDown();
    else if (cmd == ']') turnSpeedUp();
    else if (cmd == '[') turnSpeedDown();
    else if (cmd == '}') incrementUp();
    else if (cmd == '{') incrementDown();
    else if (cmd == '.') turnIncrementUp();
    else if (cmd == ',') turnIncrementDown();
    else if (cmd == '?') sendStatus();
  }
}

void sendStatus() {
  String json = "{";
  json += "\"type\":\"status\",";
  json += "\"state\":\"" + currentState + "\",";
  json += "\"speed\":" + String(speed) + ",";
  json += "\"turnSpeed\":" + String(turnSpeed) + ",";
  json += "\"increment\":" + String(increment) + ",";
  json += "\"turnIncrement\":" + String(turnIncrement);
  json += "}";
  SerialBT.println(json);
  Serial.println(json);
}

void sendAck(String action) {
  String json = "{";
  json += "\"type\":\"ack\",";
  json += "\"action\":\"" + action + "\",";
  json += "\"state\":\"" + currentState + "\",";
  json += "\"speed\":" + String(speed) + ",";
  json += "\"turnSpeed\":" + String(turnSpeed) + ",";
  json += "\"increment\":" + String(increment) + ",";
  json += "\"turnIncrement\":" + String(turnIncrement);
  json += "}";
  SerialBT.println(json);
  Serial.println(json);
}

void setSpeed() {
  analogWrite(ENA, speed);
  analogWrite(ENB, speed);
}

void forward() {
  currentState = "FORWARD";
  setSpeed();
  digitalWrite(IN1, HIGH); digitalWrite(IN2, LOW);
  digitalWrite(IN3, HIGH); digitalWrite(IN4, LOW);
  sendAck("FORWARD");
}

void backward() {
  currentState = "BACKWARD";
  setSpeed();
  digitalWrite(IN1, LOW); digitalWrite(IN2, HIGH);
  digitalWrite(IN3, LOW); digitalWrite(IN4, HIGH);
  sendAck("BACKWARD");
}

void turnLeft() {
  currentState = "LEFT";
  analogWrite(ENA, 0);
  analogWrite(ENB, turnSpeed);
  digitalWrite(IN1, LOW); digitalWrite(IN2, LOW);
  digitalWrite(IN3, HIGH); digitalWrite(IN4, LOW);
  sendAck("LEFT");
}

void turnRight() {
  currentState = "RIGHT";
  analogWrite(ENA, turnSpeed);
  analogWrite(ENB, 0);
  digitalWrite(IN1, HIGH); digitalWrite(IN2, LOW);
  digitalWrite(IN3, LOW); digitalWrite(IN4, LOW);
  sendAck("RIGHT");
}

void stopCar() {
  currentState = "STOP";
  analogWrite(ENA, 0);
  analogWrite(ENB, 0);
  digitalWrite(IN1, LOW); digitalWrite(IN2, LOW);
  digitalWrite(IN3, LOW); digitalWrite(IN4, LOW);
  sendAck("STOP");
}

void speedUp() { speed = min(255, speed + increment); sendAck("SPEED_UP"); }
void speedDown() { speed = max(0, speed - increment); sendAck("SPEED_DOWN"); }
void turnSpeedUp() { turnSpeed = min(255, turnSpeed + turnIncrement); sendAck("TURN_SPEED_UP"); }
void turnSpeedDown() { turnSpeed = max(0, turnSpeed - turnIncrement); sendAck("TURN_SPEED_DOWN"); }
void incrementUp() { increment = min(50, increment + 5); sendAck("INCREMENT_UP"); }
void incrementDown() { increment = max(1, increment - 5); sendAck("INCREMENT_DOWN"); }
void turnIncrementUp() { turnIncrement = min(50, turnIncrement + 5); sendAck("TURN_INCREMENT_UP"); }
void turnIncrementDown() { turnIncrement = max(1, turnIncrement - 5); sendAck("TURN_INCREMENT_DOWN"); }
```

---

## Command Reference

| Command | Action | ESP32 Response |
|---|---|---|
| `F` | Forward | ack: FORWARD + current values |
| `B` | Backward | ack: BACKWARD + current values |
| `L` | Turn left (right side drives, left stops) | ack: LEFT + current values |
| `R` | Turn right (left side drives, right stops) | ack: RIGHT + current values |
| `S` | Stop | ack: STOP + current values |
| `+` | Speed up by increment | ack: SPEED_UP + new speed |
| `-` | Speed down by increment | ack: SPEED_DOWN + new speed |
| `]` | Turn speed up by turnIncrement | ack: TURN_SPEED_UP + new turnSpeed |
| `[` | Turn speed down by turnIncrement | ack: TURN_SPEED_DOWN + new turnSpeed |
| `}` | Increment up by 5 | ack: INCREMENT_UP + new increment |
| `{` | Increment down by 5 | ack: INCREMENT_DOWN + new increment |
| `.` | Turn increment up by 5 | ack: TURN_INCREMENT_UP + new turnIncrement |
| `,` | Turn increment down by 5 | ack: TURN_INCREMENT_DOWN + new turnIncrement |
| `?` | Request full status | status: all current values |

---

## Bluetooth JSON Protocol

The ESP32 responds to every command with JSON. Designed to be easily parsed by a custom app.

### Status response (sent when `?` is received)
```json
{"type":"status","state":"STOP","speed":75,"turnSpeed":100,"increment":10,"turnIncrement":10}
```

### Ack response (sent after every command)
```json
{"type":"ack","action":"FORWARD","state":"FORWARD","speed":75,"turnSpeed":100,"increment":10,"turnIncrement":10}
```

### Fields

| Field | Type | Description |
|---|---|---|
| `type` | string | `"status"` or `"ack"` |
| `state` | string | Current car state: STOP, FORWARD, BACKWARD, LEFT, RIGHT |
| `action` | string | Command just executed (ack only) |
| `speed` | number | Current drive speed 0–255 |
| `turnSpeed` | number | Current turn speed 0–255 |
| `increment` | number | Current speed change step |
| `turnIncrement` | number | Current turn speed change step |

### App Integration Pattern
1. On Bluetooth connect → send `?` to get current state
2. Parse response JSON and populate UI with current values
3. After each command → parse ack JSON and update displayed values
4. Check `type` field to distinguish status vs ack messages

---

## LiPo Battery Guide

### Voltage Reference

| Voltage per cell | Total (3S) | Status |
|---|---|---|
| 4.2V | 12.6V | 100% full — after charging |
| 3.8V | 11.4V | ~50% — ideal storage charge |
| 3.7V | 11.1V | ~30% — charge soon |
| 3.5V | 10.5V | Low — stop driving now |
| 3.0V | 9.0V | ⚠️ NEVER go here — permanent damage |

### Charging
1. Place battery inside LiPo safe bag
2. Connect XT60 connector → B3 charger main input
3. Connect balance connector (small white plug) → B3 charger balance port — **BOTH must be connected**
4. Plug B3 charger into wall
5. Green lights = charging. All lights solid green = fully charged

> ⚠️ Never charge a swollen/puffy battery. Never leave charging unattended. Always charge on a hard non-flammable surface.

### Storage Rules
- Store at **3.8V per cell** (~50%) for long term storage
- Keep at room temperature — not in hot cars or direct sunlight
- Keep away from sharp objects
- Do not stack heavy objects on top
- Self-discharge is very slow (~1–2% per month) — safe to leave for weeks

---

## Troubleshooting

| Problem | Likely Cause | Fix |
|---|---|---|
| Port not showing on Mac | Missing CH340 driver | Install from wch-ic.com, restart Mac |
| Motors not responding | GND wire disconnected | Check GND connection between ESP32 and L298N |
| Car goes wrong direction | Motor wires swapped | Swap wires in OUT1/OUT2 or OUT3/OUT4 terminals |
| Only some wheels spin | Loose screw terminal | Unscrew, push wire deeper, tighten firmly |
| ESP32 loses power when USB disconnected | 5V from L298N not reaching ESP32 | Use USB power bank to power ESP32 separately |
| L298N light faint/off | Short circuit damaged chip | Order replacement L298N (~₹150) |
| Car too fast | LiPo gives more power than AA batteries | Reduce speed variable via `+`/`-` commands |
| Car won't turn | 4WD grip too strong | Use differential turning — one side stops, other drives |
| Bluetooth not found | ESP32 not powered | Check power bank connected to ESP32 |
| Upload fails | Need to press BOOT | Hold BOOT button when 'Connecting...' appears |

---

## Tips & Notes

- Always disconnect LiPo before touching any wires
- Use electrical tape to cover exposed copper on wire ends
- Mount components with double-sided tape or velcro
- Use velcro for power bank so it can be removed for charging
- Add an inline XT60 switch (~₹150–200) to easily turn car on/off
- Test on smooth tile floors for easier turning
- Stop driving when motors start slowing down — battery is getting low
- Never charge a swollen LiPo battery
- Order 2x L298N boards — cheap insurance to have a spare!

---

*Built with ESP32 WROOM-32 + L298N Motor Driver + MatLogix 11.1V 3S LiPo*
