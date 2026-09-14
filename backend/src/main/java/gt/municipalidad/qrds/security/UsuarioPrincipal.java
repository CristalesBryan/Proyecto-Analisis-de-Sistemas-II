package gt.municipalidad.qrds.security;

import gt.municipalidad.qrds.entity.Permiso;
import gt.municipalidad.qrds.entity.Usuario;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UsuarioPrincipal implements UserDetails {

    private final Usuario usuario;

    public UsuarioPrincipal(Usuario usuario) {
        this.usuario = usuario;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()));
        for (Permiso permiso : Permiso.deRol(usuario.getRol())) {
            authorities.add(new SimpleGrantedAuthority("PERM_" + permiso.name()));
        }
        return authorities;
    }

    @Override
    public String getPassword() {
        return usuario.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return usuario.getEmail();
    }

    @Override
    public boolean isAccountNonLocked() {
        return !usuario.estaBloqueado();
    }

    @Override
    public boolean isEnabled() {
        return usuario.isActivo();
    }
}
