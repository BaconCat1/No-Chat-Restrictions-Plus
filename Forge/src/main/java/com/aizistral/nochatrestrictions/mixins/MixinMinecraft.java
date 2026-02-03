package com.aizistral.nochatrestrictions.mixins;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.aizistral.nochatrestrictions.core.NCRCore;
import com.aizistral.nochatrestrictions.core.WrappedUserApiService;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.multiplayer.ClientLevel;

@Mixin(value = Minecraft.class, remap = false)
public class MixinMinecraft {

    @Shadow
    private UserApiService userApiService;

    @Unique
    private int ncr$joinCheckNonce = 0;

    @Inject(method = { "m_193585_", "createUserApiService" }, at = @At("RETURN"), cancellable = true)
    public void onCreateUserApi(YggdrasilAuthenticationService authService, GameConfig gameConfig,
	    CallbackInfoReturnable<UserApiService> info) {
	UserApiService returnedService = info.getReturnValue();
	assert returnedService != null;
	info.setReturnValue(WrappedUserApiService.wrap(returnedService));

	NCRCore.LOGGER.info("Successfully supplanted UserApiService with a wrapped version.");
    }

    @Inject(method = { "m_193584_", "getUserApiService" }, at = @At("RETURN"), cancellable = true, require = 0)
    public void onGetUserApiService(CallbackInfoReturnable<UserApiService> info) {
	UserApiService returnedService = info.getReturnValue();
	if (returnedService != null) {
	    info.setReturnValue(WrappedUserApiService.wrap(returnedService));
	}
    }

    @Inject(method = { "setLevel" }, at = @At("TAIL"))
    public void onSetLevel(ClientLevel level, CallbackInfo info) {
	if (level != null) {
	    ncr$schedulePostJoinInjectionCheck();
	}
    }

    @Inject(method = { "m_294837_", "isNameBanned" }, at = @At("HEAD"), cancellable = true)
    public void onCheckNameBan(CallbackInfoReturnable<Boolean> info) {
	info.setReturnValue(Boolean.FALSE);
    }

    @Unique
    private void ncr$schedulePostJoinInjectionCheck() {
	final int nonce = ++this.ncr$joinCheckNonce;
	CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS).execute(() -> {
	    ((Minecraft) (Object) this).execute(() -> {
		if (nonce != this.ncr$joinCheckNonce) {
		    return;
		}
		ncr$verifyUserApiInjection("post-join");
	    });
	});
    }

    @Unique
    private void ncr$verifyUserApiInjection(String context) {
	UserApiService service = this.userApiService;
	if (service == null) {
	    return;
	}
	if (!(service instanceof WrappedUserApiService)) {
	    NCRCore.LOGGER.warn("UserApiService not wrapped ({}). Re-wrapping.", context);
	    this.userApiService = WrappedUserApiService.wrap(service);
	} else {
	    NCRCore.LOGGER.info("UserApiService already wrapped ({}).", context);
	}
    }

}
