#define TRIG_PIN 4
#define ECHO_PIN 5

void setup() {
  Serial.begin(115200);
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT_PULLDOWN);
  digitalWrite(TRIG_PIN, LOW);
  delay(50);
}

void loop() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(3);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  long duration = pulseIn(ECHO_PIN, HIGH, 30000); // 30ms timeout
  float distance = duration * 0.0343 / 2.0;

  Serial.print("Duration: ");
  Serial.print(duration);
  Serial.print(" us | Distance: ");
  Serial.print(distance);
  Serial.println(" cm");

  delay(250);
}
