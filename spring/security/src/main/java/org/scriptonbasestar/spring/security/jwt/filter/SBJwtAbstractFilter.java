package org.scriptonbasestar.spring.security.jwt.filter;

import lombok.Setter;
import org.scriptonbasestar.spring.security.jwt.dto.SBAuthorizedUserClaims;
import org.scriptonbasestar.spring.security.jwt.bean.SBJwtAuthenticationManager;
import org.scriptonbasestar.spring.security.jwt.dto.SBJwtPreAuthenticateToken;
import org.scriptonbasestar.tool.core.exception.compiletime.SBTextExtractException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author archmagece
 * @since 2017-09-27
 */
public abstract class SBJwtAbstractFilter extends OncePerRequestFilter {

	@Setter
	private SBJwtAuthenticationManager authenticationManager;

	//not null
	@Setter
	protected String serviceName;
	//not null
	@Setter
	protected String signingKey;

	@Setter
	protected SBJwtSsoHandler sbJwtSsoHandler;

	@Setter
	protected AuthenticationSuccessHandler successHandler;
	@Setter
	protected AuthenticationFailureHandler failureHandler;

	@Override
	protected void initFilterBean() throws ServletException {
		super.initFilterBean();
		try{
			Assert.notNull(authenticationManager, "authenticationManager must not null");
			Assert.notNull(serviceName, "serviceName must not null");
			Assert.notNull(signingKey, "signingKey must not null");
		}catch (IllegalArgumentException e){
			throw new ServletException(e.getMessage());
		}
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		String token = null;
		try {
			token = extractTokenString(request, response);
		} catch (SBTextExtractException e) {
//			e.printStackTrace();
			filterChain.doFilter(request, response);
			return;
		}

		Authentication authResult;
		try {
			authResult = authenticationManager.authenticate(new SBJwtPreAuthenticateToken(token));
		} catch (InternalAuthenticationServiceException failed) {
			logger.error("An internal error occurred while trying to authenticate the user.", failed);
			unsuccessfulAuthentication(request, response, failed);
			filterChain.doFilter(request, response);
			return;
		} catch (AuthenticationException failed) {
			unsuccessfulAuthentication(request, response, failed);
			filterChain.doFilter(request, response);
			return;
		}

		successfulAuthentication(request, response, authResult);
		filterChain.doFilter(request, response);
	}

	protected abstract String extractTokenString(HttpServletRequest request, HttpServletResponse response) throws SBTextExtractException;


	protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
											  AuthenticationException failed)
			throws IOException, ServletException {

		SecurityContextHolder.clearContext();
		if (logger.isDebugEnabled()) {
			logger.debug("Authentication request failed: " + failed.toString(), failed);
			logger.debug("Updated SecurityContextHolder to contain null Authentication");
//			logger.debug("Delegating to authentication failure handler " + failureHandler);
		}
		//failed handler
		if (failureHandler != null) {
			failureHandler.onAuthenticationFailure(request, response, failed);
		}
	}

	protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response,
											Authentication authResult)
			throws IOException, ServletException {

		if (logger.isDebugEnabled()) {
			logger.debug("Authentication success. Updating SecurityContextHolder to contain: " + authResult);
		}
		SBAuthorizedUserClaims user = (SBAuthorizedUserClaims) authResult.getPrincipal();

		request.setAttribute(SBAuthorizedUserClaims.USER_ID, user.getUserId());
		request.setAttribute(SBAuthorizedUserClaims.USER_USERNAME, user.getUsername());
		request.setAttribute(SBAuthorizedUserClaims.USER_NICKNAME, user.getNickname());
		request.setAttribute(SBAuthorizedUserClaims.USER_ROLE, user.getUserRoles());

		SecurityContextHolder.getContext().setAuthentication(authResult);

		if(sbJwtSsoHandler != null){
			sbJwtSsoHandler.postProcessing(request, response, authResult);
		}

		//success handler
		if (successHandler != null) {
			successHandler.onAuthenticationSuccess(request, response, authResult);
		}
	}
}
