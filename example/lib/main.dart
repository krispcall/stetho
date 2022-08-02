import 'package:flutter/material.dart';
import 'package:stetho_a1/flutter_stetho.dart';
import 'package:http/http.dart' as http;

void main() {
  Stetho.initialize();
  runApp(MyApp(client: http.Client()));
}

class MyApp extends StatefulWidget {
  final http.Client client;

  MyApp({Key key, @required this.client}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {

  @override
  void initState() {
    super.initState();
  }

  fetchImage() {
    widget.client.get(
        Uri.parse('https://flutter.dev/assets/404/dash_nest-c64796b59b65042a2b40fae5764c13b7477a592db79eaf04c86298dcb75b78ea.png')  ,
      headers: {'Authorization': 'token'},
    );
  }

  fetchJson() {
    widget.client.get(
        Uri.parse('https://jsonplaceholder.typicode.com/posts/1'),
      headers: {'Authorization': 'token'},
    );
  }

  fetchError() {
    widget.client.get(Uri.parse('https://jsonplaceholder.typicode.com/postadsass/1'));
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: Text('Plugin example app'),
        ),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: <Widget>[
              Padding(
                padding: EdgeInsets.all(16.0),
                child: RaisedButton(
                  onPressed: fetchJson,
                  child: Text("Fetch json"),
                ),
              ),
              Padding(
                padding: EdgeInsets.all(16.0),
                child: RaisedButton(
                  onPressed: fetchImage,
                  child: Text("Fetch image"),
                ),
              ),
              Padding(
                padding: EdgeInsets.all(16.0),
                child: RaisedButton(
                  onPressed: fetchError,
                  child: Text("Fetch with Error"),
                ),
              )
            ],
          ),
        ),
      ),
    );
  }
}
