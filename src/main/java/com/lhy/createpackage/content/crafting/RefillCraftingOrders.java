package com.lhy.createpackage.content.crafting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.Config;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.crafting.ICraftingWatcherNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.ITickManager;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;

/**
 * After a probabilistic refill fails for missing network stock, request AE2 crafts for any
 * missing inputs that already have patterns on the grid. Results go into network storage.
 * Watches those keys so a finished (or cancelled) AE craft can wake refill immediately.
 */
public final class RefillCraftingOrders implements ICraftingWatcherNode {
    private final Supplier<IGridNode> node;
    private final Map<AEKey, Future<ICraftingPlan>> pending = new HashMap<>();
    private final Set<AEKey> watched = new HashSet<>();
    @Nullable
    private IStackWatcher watcher;
    private boolean craftJustFinished;

    public RefillCraftingOrders(Supplier<IGridNode> node) {
        this.node = Objects.requireNonNull(node);
    }

    public boolean takeCraftFinished() {
        boolean finished = craftJustFinished;
        craftJustFinished = false;
        return finished;
    }

    public void tickPending(@Nullable IGrid grid, IActionSource source) {
        if (grid == null || source == null) {
            return;
        }
        ICraftingService crafting = grid.getCraftingService();
        if (crafting != null) {
            finishPending(crafting, source);
        }
    }

    public void requestMissing(@Nullable Level level, @Nullable IGrid grid, IActionSource source,
            Iterable<GenericStack> required) {
        requestMissing(level, grid, source, required, null);
    }

    public void requestMissing(@Nullable Level level, @Nullable IGrid grid, IActionSource source,
            Iterable<GenericStack> required, @Nullable AEKey skip) {
        if (level == null || level.isClientSide() || grid == null || source == null) {
            return;
        }
        ICraftingService crafting = grid.getCraftingService();
        MEStorage storage = grid.getStorageService().getInventory();
        if (crafting == null || storage == null) {
            return;
        }
        finishPending(crafting, source);
        Set<AEKey> missingKeys = new HashSet<>();
        for (GenericStack stack : required) {
            if (stack == null || stack.amount() <= 0) {
                continue;
            }
            AEKey key = stack.what();
            if (skip != null && skip.equals(key)) {
                continue;
            }
            long available = storage.extract(key, stack.amount(), Actionable.SIMULATE, source);
            long missing = stack.amount() - Math.max(0, available);
            if (missing > 0) {
                missingKeys.add(key);
                request(level, crafting, source, key, missing);
            }
        }
        watch(missingKeys);
    }

    public void watchNone() {
        watch(Set.of());
    }

    public static Component retryLine(String translationKey, long nextRefillGameTime, long gameTime) {
        long remaining = Math.max(0, nextRefillGameTime - gameTime);
        String seconds = remaining % 20 == 0
                ? Long.toString(remaining / 20)
                : String.format("%.1f", remaining / 20.0);
        return Component.translatable(translationKey, seconds).withStyle(ChatFormatting.YELLOW);
    }

    public void clear() {
        for (Future<ICraftingPlan> job : pending.values()) {
            if (job != null && !job.isDone()) {
                job.cancel(true);
            }
        }
        pending.clear();
        watchNone();
        craftJustFinished = false;
    }

    @Override
    public void updateWatcher(IStackWatcher newWatcher) {
        watcher = newWatcher;
        applyWatches();
    }

    @Override
    public void onRequestChange(AEKey what) {
        if (what == null || !watched.contains(what)) {
            return;
        }
        IGridNode gridNode = node.get();
        IGrid grid = gridNode == null ? null : gridNode.getGrid();
        if (grid == null || grid.getCraftingService().isRequesting(what)) {
            return;
        }
        craftJustFinished = true;
        ITickManager ticks = grid.getService(ITickManager.class);
        if (ticks != null) {
            ticks.alertDevice(gridNode);
        }
    }

    @Override
    public void onCraftableChange(AEKey what) {
    }

    private void watch(Set<AEKey> keys) {
        if (watched.equals(keys)) {
            return;
        }
        watched.clear();
        watched.addAll(keys);
        applyWatches();
    }

    private void applyWatches() {
        if (watcher == null) {
            return;
        }
        watcher.reset();
        for (AEKey key : watched) {
            watcher.add(key);
        }
    }

    private void request(Level level, ICraftingService crafting, IActionSource source, AEKey key, long amount) {
        if (crafting.isRequesting(key) || crafting.getCraftingFor(key).isEmpty()) {
            return;
        }
        if (pending.containsKey(key)) {
            return;
        }
        pending.put(key, crafting.beginCraftingCalculation(level, () -> source, key, amount,
                CalculationStrategy.CRAFT_LESS));
    }

    private void finishPending(ICraftingService crafting, IActionSource source) {
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Future<ICraftingPlan> future = entry.getValue();
            if (future == null || !future.isDone()) {
                continue;
            }
            iterator.remove();
            submitIfReady(crafting, source, entry.getKey(), future);
        }
    }

    private void submitIfReady(ICraftingService crafting, IActionSource source, AEKey key,
            Future<ICraftingPlan> future) {
        if (crafting.isRequesting(key)) {
            return;
        }
        try {
            ICraftingPlan plan = future.get();
            if (plan == null) {
                return;
            }
            ICraftingSubmitResult result = crafting.submitJob(plan, null, null, false, source);
            if (result.successful() && Config.debugLoggingEnabled()) {
                CreatePackage.LOGGER.info("Submitted refill craft for {} x{}", key, plan.finalOutput() == null
                        ? "?"
                        : plan.finalOutput().amount());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ignored) {
            // Calculation failed; the next refill retry can start a new one.
        }
    }
}
