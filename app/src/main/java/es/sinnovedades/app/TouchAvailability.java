package es.sinnovedades.app;

import java.util.List;

/** Activation prerequisites, separate from the current foreground application. */
final class TouchAvailability {
    // Public Android capability/flag values; kept independent of Android for regression tests.
    static final int EXPLORE=2,PERFORM_GESTURES=32,REQUEST_EXPLORATION=4;

    static final class OtherService {
        final boolean own;
        final String name;
        final int capabilities,flags,targetSdk;
        OtherService(boolean own,String name,int capabilities,int flags,int targetSdk) {
            this.own=own; this.name=name; this.capabilities=capabilities;
            this.flags=flags; this.targetSdk=targetSdk;
        }
        boolean conflicts() {
            // Android ignores this request without the capability for target SDK 18+.
            // Older services can have a legacy grant, so retain the conservative check.
            return !own && (flags&REQUEST_EXPLORATION)!=0
                && ((capabilities&EXPLORE)!=0 || targetSdk<18);
        }
    }

    static final class Result {
        final boolean ready,reconnect;
        final String problem;
        Result(boolean ready,boolean reconnect,String problem) {
            this.ready=ready; this.reconnect=reconnect; this.problem=problem;
        }
        String message(boolean wanted) {
            // Never replace a missing capability/conflict with "waiting for WhatsApp".
            if (!ready) return problem;
            return wanted ? "Control táctil solicitado; esperando el primer gesto."
                : "Protección táctil preparada. Abre WhatsApp para comprobarla.";
        }
    }

    static Result check(boolean connected,int capabilities,List<OtherService> services) {
        if (!connected) return new Result(false,false,"Android aún no ha entregado los permisos del servicio conectado.");
        boolean explore=(capabilities&EXPLORE)!=0;
        boolean gestures=(capabilities&PERFORM_GESTURES)!=0;
        if (!explore || !gestures) {
            String missing=!explore && !gestures ? "control táctil y transmisión de pulsaciones"
                : !explore ? "control táctil" : "transmisión de pulsaciones";
            return new Result(false,true,"Android no ha habilitado "+missing
                +". En Accesibilidad, desactiva y vuelve a activar el servicio Sin Novedades.");
        }
        StringBuilder conflicts=new StringBuilder();
        for (OtherService other:services) if (other.conflicts()) {
            if (conflicts.length()>0) conflicts.append(", ");
            conflicts.append(other.name);
        }
        if (conflicts.length()>0) return new Result(false,false,
            "Protección táctil sin activar: otro servicio solicita exploración táctil: "+conflicts
            +". Revisa ese servicio en Accesibilidad.");
        return new Result(true,false,"");
    }
}
