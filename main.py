from curses.ascii import US
import logging
import os
import sys
import traceback
from shared import Users, Channels

try:
    import discord
    from discord.ext import commands
    from dotenv import load_dotenv
except ImportError:
    os.system("pip install -r requirements.txt")
    os.execl(sys.executable, sys.executable, *sys.argv)

load_dotenv()
INTENTS = discord.Intents.all()


class Bot(commands.Bot):
    async def on_ready(self):
        logging.info(f"Logged in as {self.user}")

    async def on_application_command_error(self, ctx: discord.ApplicationContext, error: discord.DiscordException):
        exc_type, exc_value, exc_traceback = type(error), error, error.__traceback__
        traceback_str = "".join(traceback.format_exception(exc_type, exc_value, exc_traceback))
        logging.error(f"Error in {ctx.command} from module {ctx.command.cog.__class__.__name__}"
                      f"\n Error message: {exc_value}\n Traceback: {traceback_str}")
        embed = discord.Embed(title="Une erreur est survenue", description=f"Erreur provoquée par {ctx.author.mention}",
                              color=discord.Color.red())
        embed = discord.Embed(
            title="Une erreur est survenue",
            description=f"Erreur provoquée par {ctx.author.mention}",
            color=discord.Color.red(),
            footer=discord.EmbedFooter(f"Veuillez transmettre ceci à <@{Users.E_PSI_LON}> ou à <@{Users.LUXIO}>")
        )\
            .add_field(name="Commande", value=f"`/{ctx.command}`")\
            .add_field(name="Module", value=f"`{ctx.command.cog.__class__.__name__!r}`")\
            .add_field(name="Message d'erreur", value=f"`{exc_value}`")\
            .add_field( name="Traceback", value=f"```\n{traceback_str[:1014]}...```")
        try:
            await ctx.respond(embed=embed, ephemeral=True)
        except Exception:
            await ctx.channel.send("Ce message se supprimera d'ici 20s", embed=embed, delete_after=20)
        finally:
            await self.get_user(Users.LUXIO).send(embed=embed)
            await self.get_user(Users.E_PSI_LON).send(embed=embed)

    async def on_error(self, event_method: str, *args, **kwargs) -> None:
        context = None
        for arg in args:
            if isinstance(arg, discord.ApplicationContext):
                context = arg
                break
        if not context:
            for arg in kwargs.values():
                if isinstance(arg, discord.ApplicationContext):
                    context = arg
                    break
        exc_type, exc_value, exc_traceback = sys.exc_info()
        traceback_str = "".join(traceback.format_exception(exc_type, exc_value, exc_traceback))
        if context is not None:
            logging.error(
                f"Error in {event_method}\n Error message: {exc_value}\n Traceback: {traceback_str}\n Args: {args}"
                f"\n Kwargs: {kwargs}")
            embed = discord.Embed(title="Une erreur est survenue",
                                  description=f"Erreur provoquée par {context.author.mention}",
                                  color=discord.Color.red())
            embed.add_field(name="Commande", value=f"`{context.command}`")
            embed.add_field(name="Module", value=f"`{context.command.cog.__class__.__name__}`")
            embed.add_field(name="Message d'erreur", value=f"`{exc_value}`")
            embed.add_field(name="Traceback", value=f"```\n{traceback_str}```")
            embed.set_footer(text=f"Veuillez transmettre ceci à <@{Users.E_PSI_LON}> ou à <@{Users.LUXIO}>")
            try:
                await context.respond(embed=embed, ephemeral=True)
            except Exception:
                await context.send("Ce message se supprimera d'ici 20s", embed=embed, delete_after=20)
            finally:
                await self.get_user(Users.LUXIO).send(embed=embed)
                await self.get_user(Users.E_PSI_LON).send(embed=embed)
        else:
            logging.error(
                f"Error in {event_method}\n Error message: {exc_value}\n Traceback: {traceback_str}\n Args: {args}"
                f"\n Kwargs: {kwargs}")

bot = Bot(intents=INTENTS)


@bot.listen("on_message")
async def on_message(message: discord.Message):
    if message.channel.id == Channels.IDEES:
        await message.create_thread(
            name=message.content if len(message.content) < 100 else message.content[:97] + "...")

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    for file in os.listdir("games"):
        if file.endswith(".py") and file != "__init__.py":
            bot.load_extension(f"games.{file[:-3]}")
            logging.info(f"Loaded game {file[:-3]}")
    bot.run(os.getenv("TOKEN"))
