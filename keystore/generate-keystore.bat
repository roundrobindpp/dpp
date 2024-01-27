set pathToKeytool="c:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2021.2.2\jbr\bin\"
set path=%pathToKeytool%;%path%
keytool -genkeypair -alias tomcat -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.p12 -validity 3650 -storepass 123123