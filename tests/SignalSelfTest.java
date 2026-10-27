import com.tomaszabramowicz.meshchat.Proto;
import com.tomaszabramowicz.meshchat.SignalInfo;

public final class SignalSelfTest {
    private static int checks=0;
    private static void check(boolean condition,String name) {
        checks++; if(!condition) throw new AssertionError(name);
    }
    public static void main(String[] args) {
        long now=1800000000000L;
        byte[] packet=new Proto.Writer().var(12,-105).fixed(8,Float.floatToIntBits(-4.5f))
            .fixed(7,now/1000-10).build();
        SignalInfo value=SignalInfo.read(packet);
        check(value!=null,"Valid receive metadata");
        check(value.rssi==-105,"Signed int32 RSSI");
        check(value.snr!=null && Math.abs(value.snr+4.5f)<0.0001f,"IEEE754 SNR decoding");
        check(value.ageMs(now)==10000,"Packet age uses radio receive time");
        check(SignalInfo.read(new Proto.Writer().fixed(8,0).build())==null,"Missing RSSI is not invented");
        check(SignalInfo.read(new Proto.Writer().var(12,0).build())==null,"Default RSSI is not a measurement");
        check(SignalInfo.read(new Proto.Writer().var(12,-90).var(14,1).build())==null,"MQTT packets excluded");
        SignalInfo absentSnr=SignalInfo.read(new Proto.Writer().var(12,-90).build());
        check(absentSnr.snr==null,"Absent SNR remains unavailable, not invented zero");
        check(absentSnr.ageMs(now)==-1,"Unknown packet age is explicit");
        SignalInfo zeroSnr=SignalInfo.read(new Proto.Writer().var(12,-100).fixed(8,0).build());
        check(zeroSnr.snr!=null && zeroSnr.snr==0f,"Explicit zero SNR is valid");
        SignalInfo nan=SignalInfo.read(new Proto.Writer().var(12,-100).fixed(8,Float.floatToIntBits(Float.NaN)).build());
        check(nan.snr==null,"NaN is not displayed");
        SignalInfo future=SignalInfo.read(new Proto.Writer().var(12,-100).fixed(7,now/1000+300).build());
        check(future.ageMs(now)==-1,"Bad future timestamp does not look fresh");
        SignalInfo old=SignalInfo.read(new Proto.Writer().var(12,-100).fixed(7,now/1000-3600).build());
        check(old.ageMs(now)==3600000,"Cached packet does not reset measurement age");
        System.out.println("PASS: "+checks+" passive signal checks");
    }
}
