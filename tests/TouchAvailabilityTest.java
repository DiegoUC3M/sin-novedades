package es.sinnovedades.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class TouchAvailabilityTest {
    private static int checks;
    private static void check(boolean value,String name) {
        checks++;
        if (!value) throw new AssertionError(name);
    }
    private static TouchAvailability.Result result(int capabilities,TouchAvailability.OtherService... others) {
        return TouchAvailability.check(true,capabilities,Arrays.asList(others));
    }
    private static TouchAvailability.OtherService other(String name,int capabilities,int flags,int target) {
        return new TouchAvailability.OtherService(false,name,capabilities,flags,target);
    }
    public static void main(String[] args) {
        TouchAvailability.Result old=result(1);
        check(!old.ready && old.reconnect,"old overlay-only connection needs reconnection");
        check(old.message(true).contains("control táctil y transmisión"),"names both missing capabilities");
        check(old.message(false).equals(old.message(true)),"leaving WhatsApp preserves the missing-capability explanation");
        check(old.message(false).contains("desactiva y vuelve a activar"),"preserves actionable reconnect instruction");
        check(!result(3).ready && result(3).problem.contains("transmisión"),"must be able to replay allowed taps before intercepting input");
        check(!result(33).ready && result(33).reconnect,"gesture capability alone cannot intercept physical input");
        TouchAvailability.Result ready=result(35);
        check(ready.ready && !ready.reconnect,"fresh connection has both required capabilities");
        check(ready.message(true).contains("esperando el primer gesto"),"configuration does not claim input has arrived");
        check(ready.message(false).contains("preparada"),"valid idle state remains distinct from an activation failure");
        check(result(35,other("Bloqueador",1,4,36)).ready,"flag without capability is ignored by modern Android, not a conflict");
        check(result(35,other("Observador",35,0,36)).ready,"another accessibility permission alone is not a conflict");
        check(result(35,new TouchAvailability.OtherService(true,"Sin Novedades",35,4,36)).ready,"own active exploration does not block itself");
        TouchAvailability.Result conflict=result(35,other("Lector de pantalla",3,4,36));
        check(!conflict.ready && !conflict.reconnect,"genuine other explorer is distinguished from missing permission");
        check(conflict.problem.contains("Lector de pantalla"),"identifies the conflicting service by name");
        check(conflict.message(false).equals(conflict.message(true)),"conflict is not hidden when opening our app");
        check(!result(35,other("Lector antiguo",0,4,17)).ready,"legacy exploration requests may have a grant");
        check(result(35,other("Lector moderno",0,4,18)).ready,"capability requirement starts at target 18");
        TouchAvailability.Result multiple=result(35,other("Lector A",3,4,36),other("Lector B",3,4,36));
        check(multiple.problem.contains("Lector A, Lector B"),"reports all conflicting services");
        check(result(35).ready,"removing conflict or reconnecting recovers without reinstall");
        List<TouchAvailability.OtherService> none=Collections.emptyList();
        check(!TouchAvailability.check(false,35,none).ready,"unconnected service is never ready");
        System.out.println("TouchAvailability: "+checks+" checks passed");
    }
}
