from shared.utils import *


async def message_callback(self, interaction: discord.Interaction):
    if self.notif:
        message = f"@everyone\n {self.children[0].value}"
    else:
        message = self.children[0].value
    webhook = await get_webhook(self.bot, self.channel, "🔋")
    await webhook.send(message, username="ParalyaBTW", avatar_url="https:/")
    await interaction.response.send_message("Message envoyé !", ephemeral=True)


async def invalid_team(self, interaction: discord.Interaction) -> tuple[str | None, int | None, discord.Webhook | None]:
    obj = self.children[0].value
    team = self.children[1].value
    # S'il n'y a pas de team, la team est celle de l'utilisateur
    if not team:
        team = self.ctx.channel.parent_id
    else:
        if team.isdigit() and int(team) <= len(self.teams):
            team = int(team)
            team = self.teams[team - 1].value
        else:
            await interaction.response.send_message("Équipe invalide", ephemeral=True,
                                                    view=Retry(self.__class__, self.ctx))
            return None, None, None
    webhook = await get_webhook(self.ctx.bot, self.ctx.channel.parent_id, "🔋")
    return obj, team, webhook
