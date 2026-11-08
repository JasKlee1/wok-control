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
    public static final String CLAIM_MESA = "mesaId";
    public static final String CLAIM_SESION = "sesionMesa";

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
     * v2 · HU-036 (ADR-002): token de la sesión de mesa que abre el QR. No tiene usuario ni
     * contraseña: lleva el rol CLIENTE, el permiso CARTA_CLIENTE y la mesa a la que pertenece. La
     * sesión además se valida en pedidos-service en cada petición, porque vence al cobrar la cuenta.
     */
    public TokenEmitido emitirTokenMesa(String sesionId, String mesaId, int mesaNumero) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(props.expirationMinutes(), ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject("mesa:" + mesaNumero)
                .issuedAt(ahora)
                .expiresAt(expira)
                .claim(CLAIM_TIPO, "mesa")
                .claim(CLAIM_NOMBRE, "Mesa " + mesaNumero)
                .claim(CLAIM_ROLES, List.of(Roles.CLIENTE))
                .claim(CLAIM_PERMISOS, List.of(Permisos.CARTA_CLIENTE))
                .claim(CLAIM_MESA, mesaId)
                .claim(CLAIM_SESION, sesionId)
                .build();
        return new TokenEmitido(codificar(claims), expira);
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
