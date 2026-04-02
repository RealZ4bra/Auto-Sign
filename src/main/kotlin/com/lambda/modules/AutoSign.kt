package com.lambda.modules

import com.lambda.ExamplePlugin
import com.lambda.client.module.Category
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.gui.inventory.GuiEditSign
import net.minecraft.network.play.client.CPacketUpdateSign
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.client.event.GuiOpenEvent

/*
 * @author ToxicAven (Modified for AutoSign)
 */

internal object AutoSign : PluginModule(
    name = "AutoSign",
    category = Category.MISC,
    description = "Automatically writes text on signs when you place them",
    pluginMain = DupePlugin
) {
    private val line1 by setting("Line 1", "Default Text")
    private val line2 by setting("Line 2", "")
    private val line3 by setting("Line 3", "")
    private val line4 by setting("Line 4", "")
    private val autoClose by setting("Auto Close", true)

    init {
        safeListener<GuiOpenEvent> { event ->
            val gui = event.gui
            
            // Check if the GUI being opened is the Sign Edit screen
            if (gui is GuiEditSign) {
                val tileSign = gui.tileSign
                
                // Construct the text array from settings
                val lines = arrayOf(
                    TextComponentString(line1),
                    TextComponentString(line2),
                    TextComponentString(line3),
                    TextComponentString(line4)
                )

                // Send the update packet to the server
                connection.sendPacket(CPacketUpdateSign(tileSign.pos, lines))

                if (autoClose) {
                    // Cancel the event so the GUI never actually stays open on your screen
                    event.isCanceled = true
                    mc.displayGuiScreen(null)
                    MessageSendHelper.sendChatMessage("Sign automatically signed.")
                }
            }
        }
    }
}
    description = "Example module which automatically switchs to the best tools when mining or attacking",
    pluginMain = ExamplePlugin
) {
    private val switchBack = setting("Switch Back", true)
    private val timeout by setting("Timeout", 20, 1..100, 5, { switchBack.value })
    private val swapWeapon by setting("Switch Weapon", false)
    private val preferWeapon by setting("Prefer", CombatUtils.PreferWeapon.SWORD)

    private var shouldMoveBack = false
    private var lastSlot = 0
    private var lastChange = 0L

    init {
        safeListener<PlayerInteractEvent.LeftClickBlock> {
            if (shouldMoveBack || !switchBack.value) equipBestTool(world.getBlockState(it.pos))
        }

        safeListener<PlayerAttackEvent> {
            if (swapWeapon && it.entity is EntityLivingBase) equipBestWeapon(preferWeapon)
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (mc.currentScreen != null || !switchBack.value) return@safeListener

            val mouse = Mouse.isButtonDown(0)
            if (mouse && !shouldMoveBack) {
                lastChange = System.currentTimeMillis()
                shouldMoveBack = true
                lastSlot = player.inventory.currentItem
                playerController.syncCurrentPlayItem()
            } else if (!mouse && shouldMoveBack && (lastChange + timeout * 10 < System.currentTimeMillis())) {
                shouldMoveBack = false
                player.inventory.currentItem = lastSlot
                playerController.syncCurrentPlayItem()
            }
        }
    }

    private fun SafeClientEvent.equipBestTool(blockState: IBlockState) {
        player.hotbarSlots.maxByOrNull {
            val stack = it.stack
            if (stack.isEmpty) {
                0.0f
            } else {
                var speed = stack.getDestroySpeed(blockState)

                if (speed > 1.0f) {
                    val efficiency = EnchantmentHelper.getEnchantmentLevel(Enchantments.EFFICIENCY, stack)
                    if (efficiency > 0) {
                        speed += efficiency * efficiency + 1.0f
                    }
                }

                speed
            }
        }?.let {
            swapToSlot(it)
        }
    }

    init {
        switchBack.valueListeners.add { _, it ->
            if (!it) shouldMoveBack = false
        }
    }
}
