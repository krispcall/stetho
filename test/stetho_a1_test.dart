import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:stetho_a1/stetho_a1.dart';

void main() {
  const MethodChannel channel = MethodChannel('stetho_a1');

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });

  test('getPlatformVersion', () async {
    expect(await StethoA1.platformVersion, '42');
  });
}
