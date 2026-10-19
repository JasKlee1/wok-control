package com.wokcontrol.common.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Emite tokens JWT de usuario (solo usuarios-service los usa para el login) y tokens internos de
 * servicio, de vida corta, para las llamadas entre microservicios.
 */
@Service
public class TokenService {

    public static final String CLAIM_NOMBRE = "nombre";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_PERMISOS = "permisos";
    public static final String CLAIM_PROVEEDOR = "proveedorId";
    public static final String CLAIM_TIPO = "tipo";
    public static final String CLAIM_ACTOR = "actor";

    private final JwtEncoder encoder;
    private final JwtProperties props;

    public TokenService(JwtEncoder encoder, JwtProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public record TokenEmitido(String token, Instant expiraEn) {
    }

    public TokenEmitido emitirTokenUsuario(String username, String nombre, Collection<String> roles,
            Collection<String> permisos, String proveedorId) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(props.expirationMinutes(), ChronoUnit.MINUTES);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(username)
                .issuedAt(ahora)
                .expiresAt(expira)
                .claim(CLAIM_TIPO, "usuario")
                .claim(CLAIM_NOMBRE, nombre)
                .claim(CLAIM_ROLES, List.copyOf(roles))
                .claim(CLAIM_PERMISOS, List.copyOf(permisos));
        if (proveedorId != null) {
            claims.claim(CLAIM_PROVEEDOR, proveedorId);
        }
        return new TokenEmitido(codificar(claims.build()), expira);
    }

    /**
     * Token interno con el permiso SISTEMA. El claim "actor" conserva el usuario que originó la
     * operación, para auditoría.
     */
    public String emitirTokenServicio(String servicio, String actor) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject("servicio:" + servicio)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(props.serviceTokenMinutes(), ChronoUnit.MINUTES))
                .claim(CLAIM_TIPO, "servicio")
                .claim(CLAIM_NOMBRE, servicio)
                .claim(CLAIM_ROLES, List.of())
                .claim(CLAIM_PERMISOS, List.of(Permisos.SISTEMA))
                .claim(CLAIM_ACTOR, actor == null ? "sistema" : actor)
                .build();
        return codificar(claims);
    }

    private String codificar(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
