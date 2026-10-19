package com.wokcontrol.common.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Convierte el JWT en autoridades: cada permiso como autoridad simple y cada rol como ROLE_*.
 */
public class WokJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        UsuarioActual u = UsuarioActual.desdeJwt(jwt);
        u.permisos().forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        u.roles().forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
