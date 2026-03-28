#include "BluetoothSerial.h"

BluetoothSerial SerialBT;

#define IN1 26
#define IN2 27
#define IN3 14
#define IN4 12
#define ENA 25
#define ENB 33

int speed = 75;
int turnSpeed = 100;
int increment = 10;
int turnIncrement = 10;
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

void speedUp() {
  speed = min(255, speed + increment);
  sendAck("SPEED_UP");
}

void speedDown() {
  speed = max(0, speed - increment);
  sendAck("SPEED_DOWN");
}

void turnSpeedUp() {
  turnSpeed = min(255, turnSpeed + turnIncrement);
  sendAck("TURN_SPEED_UP");
}

void turnSpeedDown() {
  turnSpeed = max(0, turnSpeed - turnIncrement);
  sendAck("TURN_SPEED_DOWN");
}

void incrementUp() {
  increment = min(50, increment + 5);
  sendAck("INCREMENT_UP");
}

void incrementDown() {
  increment = max(1, increment - 5);
  sendAck("INCREMENT_DOWN");
}

void turnIncrementUp() {
  turnIncrement = min(50, turnIncrement + 5);
  sendAck("TURN_INCREMENT_UP");
}

void turnIncrementDown() {
  turnIncrement = max(1, turnIncrement - 5);
  sendAck("TURN_INCREMENT_DOWN");
}