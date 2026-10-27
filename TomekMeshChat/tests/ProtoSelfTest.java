import com.tomaszabramowicz.meshchat.Proto;
import java.util.*;
import java.nio.charset.StandardCharsets;

public final class ProtoSelfTest {
    static int checks=0;
    static void check(boolean yes,String message) {
        checks++; if(!yes) throw new AssertionError(message);
    }
    static void invalid(byte[] b) {
        boolean bad=false;
        try { Proto.parse(b); } catch(IllegalArgumentException e) { bad=true; }
        check(bad,"Malformed protobuf was accepted");
    }
    public static void main(String[] args) {
        check(Arrays.equals(Proto.config(1),new byte[]{0x18,1}),"want_config_id field");
        check(Arrays.equals(Proto.config(300),new byte[]{0x18,(byte)0xac,2}),"varint encoding");
        byte[] max=new Proto.Writer().fixed(2,0xffffffffL).build();
        check(Arrays.equals(max,new byte[]{0x15,-1,-1,-1,-1}),"fixed32 little endian");
        check(Proto.num(Proto.parse(max),2)==0xffffffffL,"uint32 round trip");
        String text="Hello, Tomek! Zażółć 🛰️";
        List<Proto.Field> outer=Proto.parse(Proto.textPacket(text,0,0x12345678L));
        List<Proto.Field> packet=Proto.parse(Proto.blob(outer,1));
        check(Proto.num(packet,2)==0xffffffffL,"broadcast destination");
        check(Proto.num(packet,3)==0,"primary channel index");
        check(Proto.num(packet,6)==0x12345678L,"packet id");
        check(Proto.num(packet,9)==3,"hop limit");
        List<Proto.Field> data=Proto.parse(Proto.blob(packet,4));
        check(Proto.num(data,1)==1,"text app port");
        check(Proto.text(data,2).equals(text),"UTF-8 round trip");
        check(Proto.num(Proto.parse(new byte[]{0x08,(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff,0x0f}),1)==0xffffffffL,"uint32 node number");
        byte[] raw=Proto.config(34567), framed=Proto.frame(raw);
        check((framed[0]&255)==0x94 && (framed[1]&255)==0xc3,"stream magic");
        check((framed[3]&255)==raw.length,"stream length");
        ArrayList<byte[]> received=new ArrayList<>();
        Proto.Framer f=new Proto.Framer();
        f.accept("boot debug\n".getBytes(StandardCharsets.UTF_8),received::add);
        f.accept(new byte[]{(byte)0x94,(byte)0x94},received::add);
        for(byte b:framed) f.accept(new byte[]{b},received::add);
        check(received.size()==1 && Arrays.equals(raw,received.get(0)),"split frame and debug resync");
        f.accept(new byte[]{(byte)0x94,(byte)0xc3,3,0},received::add);
        f.accept(framed,received::add);
        check(received.size()==2,"oversized frame recovery");
        for(int length:new int[]{1,20,127,128,255,256,512}) {
            byte[] payload=new byte[length]; new Random(length).nextBytes(payload);
            ArrayList<byte[]> out=new ArrayList<>(); Proto.Framer ff=new Proto.Framer();
            for(byte b:Proto.frame(payload)) ff.accept(new byte[]{b},out::add);
            check(out.size()==1 && Arrays.equals(payload,out.get(0)),"frame length "+length);
        }
        invalid(new byte[]{0}); invalid(new byte[]{8,(byte)0x80});
        invalid(new byte[]{0x0a,5,1}); invalid(new byte[]{0x0d,1,2});
        invalid(new byte[]{0x0b});
        byte[] overflow=new byte[11]; overflow[0]=8; Arrays.fill(overflow,1,11,(byte)0xff); invalid(overflow);
        List<Proto.Field> duplicate=Proto.parse(new byte[]{8,1,8,2});
        check(Proto.num(duplicate,1)==2,"last scalar wins");
        System.out.println("PASS: "+checks+" protocol checks");
    }
}
