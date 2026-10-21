package com.itb;

import com.itb.config.JwtAuthenticationFilter;
import com.itb.repository.*;
import com.itb.service.JwtService;
import com.itb.service.OrderAccessService;
import com.itb.service.PushNotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.SecurityFilterChain;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Starts the production component graph with synthetic persistence boundaries.
 * No database connection or HTTP listener is created.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.expiration=60000", "jwt.order-expiration=60000",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
class BackendApplicationTests {

    @Autowired private ApplicationContext context;
    @MockBean private JdbcTemplate jdbc;
    @MockBean private EntityManager entityManager;
    @MockBean private UsuarioRepository usuarios;
    @MockBean private ClienteRepository clientes;
    @MockBean private PerfilRepository perfis;
    @MockBean private OrderRepository pedidos;
    @MockBean private ItemPedidoRepository itens;
    @MockBean private ProductRepository produtos;
    @MockBean private CategoryRepository categorias;
    @MockBean private MesaRepository mesas;
    @MockBean private StatusPedidoRepository status;
    @MockBean private PaymentRepository pagamentos;

    @Test
    void productionComponentGraphLoadsWithoutDatabaseConnections() {
        assertNotNull(context.getBean(SecurityFilterChain.class));
        assertNotNull(context.getBean(JwtAuthenticationFilter.class));
        assertNotNull(context.getBean(OrderAccessService.class));
        assertNotNull(context.getBean(PushNotificationService.class));
        assertTrue(context.containsBean("clientInboundChannel"));
        assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
        assertTrue(context.getBeansOfType(EntityManagerFactory.class).isEmpty());
        JwtService jwt = context.getBean(JwtService.class);
        assertTrue(jwt.isOrderTokenValid(jwt.generateOrderToken(42L), 42L));
    }

}

