package vn.gov.drvn.kcht.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.gov.drvn.kcht.entity.AppPermissionEntity;
import vn.gov.drvn.kcht.entity.AppRoleEntity;
import vn.gov.drvn.kcht.entity.AppUserEntity;
import vn.gov.drvn.kcht.repository.AppPermissionRepository;
import vn.gov.drvn.kcht.repository.AppRoleRepository;
import vn.gov.drvn.kcht.repository.AppUserRepository;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;
    private final AppRoleRepository appRoleRepository;
    private final AppPermissionRepository appPermissionRepository;

    public CustomUserDetailsService(AppUserRepository appUserRepository,
                                    AppRoleRepository appRoleRepository,
                                    AppPermissionRepository appPermissionRepository) {
        this.appUserRepository = appUserRepository;
        this.appRoleRepository = appRoleRepository;
        this.appPermissionRepository = appPermissionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        AppUserEntity user = appUserRepository.findByUsername(usernameOrEmail)
                .or(() -> appUserRepository.findByEmail(usernameOrEmail))
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng: " + usernameOrEmail));

        AppRoleEntity role = appRoleRepository.findById(user.getRoleId())
                .orElse(null);

        List<AppPermissionEntity> perms = appPermissionRepository.findByRoleId(user.getRoleId());

        return UserPrincipal.create(user, role, perms);
    }
}
