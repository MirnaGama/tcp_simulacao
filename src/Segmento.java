import java.io.Serializable;
import java.math.BigInteger;
import java.security.*;

public class Segmento implements Serializable {
    
    public boolean isAck, isSyn, isFin, isRst;
    public int ackNum, seqNum;
    public final String hash; // hash para o checksum
    public int dados;
    
    public Segmento(int seqNum, int ackNum, boolean isSyn, boolean isAck, boolean isFin, boolean isRst, int dados) {
        this.isAck = isAck;
        this.isSyn = isSyn;
        this.seqNum = seqNum;
        this.ackNum = ackNum;
        this.isFin = isFin;
        this.isRst = isRst;
        this.dados = dados;
        this.hash = gerarHash();
    }
    
    public String toString() {
        return "[seqNum = "+seqNum+", ackNum = "+ackNum+", isSyn = "+isSyn+", isAck = "+isAck+", isFin = "+isFin+", isRst = "+isRst+", dados = "+dados+" bytes]";
}
    
    // o checksum
    public String gerarHash() {
    
            String hash = null;
            String cabecalho = this.toString();
            
         try {   
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.update(cabecalho.getBytes(),0,cabecalho.length());
           
            
            hash = (String) new BigInteger(1,m.digest()).toString(16);
            
            } catch (NoSuchAlgorithmException ex) {
            //
        }
         
           return hash;
   }
    
    public boolean checkSum() {
        String hash_novo = gerarHash();
        boolean result = false;
        
        if (this.hash.equals(hash_novo)) {
            result = true;
        }
        
        return result;
    }
    
}