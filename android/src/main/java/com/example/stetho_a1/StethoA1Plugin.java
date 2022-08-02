package com.example.stetho_a1;

import androidx.annotation.NonNull;


import com.facebook.stetho.Stetho;
import com.facebook.stetho.dumpapp.DumperPlugin;
import com.facebook.stetho.inspector.network.DefaultResponseHandler;
import com.facebook.stetho.inspector.network.NetworkEventReporter;
import com.facebook.stetho.inspector.network.NetworkEventReporterImpl;
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsDomain;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;

/** StethoA1Plugin */
public class StethoA1Plugin implements FlutterPlugin, MethodCallHandler {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private MethodChannel channel;

  public final static String TAG = "FlutterStethoPlugin";
  private final NetworkEventReporter mEventReporter = NetworkEventReporterImpl.get();
  private final Map<String, PipedInputStream> inputs = new HashMap<>();
  private final Map<String, PipedOutputStream> outputs = new HashMap<>();
  private final Map<String, FlutterStethoInspectorResponse> responses = new HashMap<>();
  private final Map<String, LinkedBlockingQueue<QueueItem>> queues = new HashMap<>();
  private Stetho.Initializer initializer;


  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "stetho_a1");
    channel.setMethodCallHandler(this);

        initializer = new Stetho.Initializer(flutterPluginBinding.getApplicationContext()) {
      @Override
      protected Iterable<DumperPlugin> getDumperPlugins() {
        return new Stetho.DefaultDumperPluginsBuilder(flutterPluginBinding.getApplicationContext()).finish();
      }

      @Override
      protected Iterable<ChromeDevtoolsDomain> getInspectorModules() {
        return new Stetho.DefaultInspectorModulesBuilder(flutterPluginBinding.getApplicationContext()).finish();
      }
    };
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
//    if (call.method.equals("getPlatformVersion")) {
//      result.success("Android " + android.os.Build.VERSION.RELEASE);
//    } else {
//      result.notImplemented();
//    }
    Log.d("this--------------method,",call.method);
    switch (call.method) {
      case "initialize":
        Stetho.initialize(initializer);
        result.success(null);
        break;
      case "requestWillBeSent":
        requestWillBeSent((Map<String, Object>) call.arguments);
        break;
      case "responseHeadersReceived":
        responseHeadersReceived(((Map<String, Object>) call.arguments));
        break;
      case "interpretResponseStream":
        interpretResponseStream(((String) call.arguments));
        break;
      case "onDataReceived":
        onDataReceived((Map<String, Object>) call.arguments);
        break;
      case "onDone":
        onDone((String) call.arguments);
        break;
      case "responseReadFinished":
        mEventReporter.responseReadFinished(((String) call.arguments));
        break;
      case "responseReadFailed":
        final List<String> idError = ((List<String>) call.arguments);
        mEventReporter.responseReadFailed(idError.get(0), idError.get(1));
        break;
      default:
        result.notImplemented();
        break;
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
  }


  private void onDone(String id) {
    final PipedOutputStream pipedOutputStream = outputs.get(id);
    final LinkedBlockingQueue<QueueItem> doneQueue = queues.get(id);
    try {
      doneQueue.put(new NullQueueItem());
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private void onDataReceived(Map<String, Object> arguments) {
    final String dataId = ((String) arguments.get("id"));
    final byte[] data = ((byte[]) arguments.get("data"));
    final LinkedBlockingQueue<QueueItem> queue = queues.get(dataId);
    try {
      queue.put(new ByteQueueItem(data));
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    mEventReporter.dataReceived(dataId,data.length,data.length);
  }

  private void responseHeadersReceived(Map<String, Object> arguments) {
    FlutterStethoInspectorResponse response = new FlutterStethoInspectorResponse(arguments);
    responses.put(response.requestId(), response);
    mEventReporter.responseHeadersReceived(response);
  }

  private void requestWillBeSent(Map<String, Object> arguments) {
    mEventReporter.requestWillBeSent(new FlutterStethoInspectorRequest(
            (arguments)
    ));
  }

  private void interpretResponseStream(final String interpretedResponseId) {
    try {
      final PipedInputStream in = new PipedInputStream();
      final PipedOutputStream out = new PipedOutputStream(in);
      final LinkedBlockingQueue<QueueItem> queue = new LinkedBlockingQueue<>();
      inputs.put(interpretedResponseId, in);
      outputs.put(interpretedResponseId, out);
      queues.put(interpretedResponseId, queue);

      new Thread(new Runnable() {
        @Override
        public void run() {
          try {
            QueueItem item;
            while ((item = queue.take()) instanceof ByteQueueItem) {
              out.write(((ByteQueueItem) item).bytes);
            }
            out.close();
          } catch (IOException e) {
            e.printStackTrace();
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }, interpretedResponseId + "src").start();

      new Thread(new Runnable() {
        @Override
        public void run() {
          final InputStream in2 = mEventReporter.interpretResponseStream(
                  interpretedResponseId,
                  responses.get(interpretedResponseId).firstHeaderValue("content-type"),
                  null,
                  in,
                  new DefaultResponseHandler(mEventReporter, interpretedResponseId));
          try {
            int item;
            while ((item = in2.read()) != -1) ;
            in.close();
            in2.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }, interpretedResponseId + "dst").start();
    } catch (IOException e) {
      mEventReporter.responseReadFailed(interpretedResponseId, e.getMessage());
    }
  }

  interface QueueItem {
  }

  class ByteQueueItem implements QueueItem {
    final byte[] bytes;

    ByteQueueItem(byte[] bytes) {
      this.bytes = bytes;
    }
  }

  class NullQueueItem implements QueueItem {
  }
}
