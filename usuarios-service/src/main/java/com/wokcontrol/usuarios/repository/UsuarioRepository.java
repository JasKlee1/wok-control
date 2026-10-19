package com.wokcontrol.usuarios.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.usuarios.entity.Usuario;

public interface UsuarioRepository extends MongoRepository<Usuario, String> {

    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    long countByRolesContainingAndActivoTrue(String rol);
}
