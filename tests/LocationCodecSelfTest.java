import com.tomaszabramowicz.meshchat.LocationCodec;
public final class LocationCodecSelfTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
 String n="1234abcdef00";String s=LocationCodec.location(n,55.953251,-3.188267,1800000000000L,12.5f);LocationCodec.Record r=LocationCodec.parse(s);
 check(r!=null&&!r.request);check(Math.abs(r.lat-55.953251)<.000001);check(Math.abs(r.lon+3.188267)<.000001);check(r.time==1800000000000L);check(r.accuracy==12.5f);check(LocationCodec.parse(LocationCodec.request(n)).request);check(LocationCodec.parse("hello")==null);check(LocationCodec.parse("TMC3|L|1234abcdef00|NaN|0|1800000000000|1")==null);check(LocationCodec.parse("TMC3|L|1234abcdef00|56|181|1800000000000|1")==null);check(LocationCodec.parse("TMC3|L|1234abcdef00|56|-3|0|1")==null);check(s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<180);System.out.println("Location codec tests passed");
 }
}
