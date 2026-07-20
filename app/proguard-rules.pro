# NanoHTTPD
-keep class fi.iki.elonen.** { *; }

# jUPnP
-keep class org.jupnp.** { *; }
-keep class org.fourthline.cling.** { *; }

# jUPnP references JDK com.sun.net.httpserver (not available on Android)
-dontwarn com.sun.net.httpserver.**

# jUPnP has optional OSGi integration (not available on Android)
-dontwarn org.osgi.**
