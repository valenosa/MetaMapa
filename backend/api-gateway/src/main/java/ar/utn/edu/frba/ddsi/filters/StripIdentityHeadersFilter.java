package ar.utn.edu.frba.ddsi.filters;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Borra de TODAS las requests entrantes los headers de identidad (X-User-Id, X-User-Name).
 * Solo AuthenticationFilter los vuelve a agregar, a partir de un token válido.
 * Sin esto, en las rutas con autenticación opcional (required: false) un cliente sin token
 * podía mandar su propio X-User-Id y hacerse pasar por otro usuario.
 */
@Component
public class StripIdentityHeadersFilter implements GlobalFilter, Ordered {

  private static final String USER_ID_HEADER = "X-User-Id";
  private static final String USER_NAME_HEADER = "X-User-Name";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest().mutate()
        .headers(headers -> {
          headers.remove(USER_ID_HEADER);
          headers.remove(USER_NAME_HEADER);
        })
        .build();

    return chain.filter(exchange.mutate().request(request).build());
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE; // Corre antes que los filtros de cada ruta (incluido AuthenticationFilter)
  }
}
